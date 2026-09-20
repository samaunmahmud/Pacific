package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.LedgerEntry;
import com.pacific.marketplace.domain.LedgerType;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.repo.LedgerEntryRepository;
import com.pacific.marketplace.repo.SellerProfileRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.SellerDtos.EarningsDto;
import com.pacific.marketplace.web.dto.SellerDtos.LedgerEntryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bookkeeping for seller earnings. When an order is delivered the seller is credited with the sale (items plus
 * shipping) and debited the marketplace commission (on the item subtotal only). Admins record payouts against the
 * balance. No real money moves here.
 */
@Service
public class LedgerService {

    private final LedgerEntryRepository ledger;
    private final SellerProfileRepository sellers;

    public LedgerService(LedgerEntryRepository ledger, SellerProfileRepository sellers) {
        this.ledger = ledger;
        this.sellers = sellers;
    }

    /** Called exactly once, when an order becomes DELIVERED. Safe to call again: it never double-books. */
    @Transactional
    public void recordDelivered(Order order) {
        SellerProfile seller = order.getSeller();
        if (seller == null) return; // Pacific's own products have no seller to pay
        if (ledger.existsByOrderIdAndType(order.getId(), LedgerType.SALE)) return;

        String note = "Order #" + order.getId();
        ledger.save(new LedgerEntry(seller, order.getId(), LedgerType.SALE, order.getTotal(), note));
        BigDecimal rate = order.getCommissionRate() == null ? BigDecimal.ZERO : order.getCommissionRate();
        BigDecimal commission = order.getSubtotal().multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (commission.signum() > 0) {
            ledger.save(new LedgerEntry(seller, order.getId(), LedgerType.COMMISSION, commission.negate(),
                    note + " · " + rate.stripTrailingZeros().toPlainString() + "% commission"));
        }
    }

    /**
     * A refund for returned goods. The seller gives back what was refunded, and the marketplace gives back its
     * commission on the goods part of it (delivery carries no commission). Called once per refund; nothing happens for
     * Pacific's own products (no seller) or an order whose sale was never booked.
     *
     * @param refund    everything refunded to the customer
     * @param goodsPart the part of it that was for goods rather than delivery
     */
    @Transactional
    public void recordRefund(Order order, BigDecimal refund, BigDecimal goodsPart, String note) {
        SellerProfile seller = order.getSeller();
        if (seller == null || !ledger.existsByOrderIdAndType(order.getId(), LedgerType.SALE)) return;
        ledger.save(new LedgerEntry(seller, order.getId(), LedgerType.REFUND, refund.negate(), note));
        BigDecimal rate = order.getCommissionRate() == null ? BigDecimal.ZERO : order.getCommissionRate();
        BigDecimal commissionBack = goodsPart.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (commissionBack.signum() > 0) {
            ledger.save(new LedgerEntry(seller, order.getId(), LedgerType.COMMISSION_REFUND, commissionBack,
                    note + " · commission returned"));
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(Long sellerId) {
        return ledger.balance(sellerId).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public EarningsDto earnings(Long sellerId, int page, int size) {
        // commission is what the marketplace kept: charged on delivery, less what it gave back on refunds
        BigDecimal commission = ledger.sumByType(sellerId, LedgerType.COMMISSION).negate()
                .subtract(ledger.sumByType(sellerId, LedgerType.COMMISSION_REFUND));
        return new EarningsDto(balance(sellerId),
                ledger.sumByType(sellerId, LedgerType.SALE).setScale(2, RoundingMode.HALF_UP),
                commission.setScale(2, RoundingMode.HALF_UP),
                ledger.sumByType(sellerId, LedgerType.PAYOUT).negate().setScale(2, RoundingMode.HALF_UP),
                ledger.sumByType(sellerId, LedgerType.REFUND).negate().setScale(2, RoundingMode.HALF_UP),
                entries(sellerId, page, size));
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryDto> entries(Long sellerId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(ledger.findBySellerId(sellerId, pageable), LedgerEntryDto::from);
    }

    /** Records a payout. The seller row is locked so two simultaneous payouts can't overdraw the balance. */
    @Transactional
    public LedgerEntryDto payout(Long sellerId, BigDecimal amount, String note) {
        SellerProfile seller = sellers.lockById(sellerId).orElseThrow(() -> ApiException.notFound("Seller not found."));
        BigDecimal payout = amount.setScale(2, RoundingMode.HALF_UP);
        if (ledger.balance(seller.getId()).compareTo(payout) < 0) {
            throw ApiException.conflict("That's more than the seller's current balance.");
        }
        return LedgerEntryDto.from(ledger.saveAndFlush(
                new LedgerEntry(seller, null, LedgerType.PAYOUT, payout.negate(), Text.clean(note))));
    }
}
