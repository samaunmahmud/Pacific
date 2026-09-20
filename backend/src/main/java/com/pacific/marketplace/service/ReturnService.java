package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.Money;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderEventType;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.ReturnItem;
import com.pacific.marketplace.domain.ReturnRequest;
import com.pacific.marketplace.domain.ReturnStatus;
import com.pacific.marketplace.notify.NotificationService;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.ReturnRequestRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.ReturnDtos.ReturnDto;
import com.pacific.marketplace.web.dto.ReturnDtos.ReturnLine;
import com.pacific.marketplace.web.dto.ReturnDtos.ReturnRequestBody;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns after delivery: a customer asks to send items back, the seller (or an admin) approves or declines, and once
 * the goods are back the seller refunds. A refund is money and bookkeeping in one step: the card is refunded (or, for
 * pay on delivery, the seller settles it directly), the seller's ledger repays the sale and the marketplace repays its
 * commission on the goods, and the goods can go back on sale.
 *
 * <p>Every change locks the order row first and reads the return after, so two people acting on the same return, or a
 * customer cancelling while a seller approves, can never both win.
 */
@Service
public class ReturnService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Europe/London"));

    private final OrderRepository orders;
    private final ReturnRequestRepository returns;
    private final ProductRepository products;
    private final LedgerService ledger;
    private final PaymentService payments;
    private final NotificationService notifications;
    private final String currency;

    public ReturnService(OrderRepository orders, ReturnRequestRepository returns, ProductRepository products,
                         LedgerService ledger, PaymentService payments, NotificationService notifications,
                         AppProperties props) {
        this.orders = orders;
        this.returns = returns;
        this.products = products;
        this.ledger = ledger;
        this.payments = payments;
        this.notifications = notifications;
        this.currency = props.shop().currency();
    }

    // ---------- customer ----------

    @Transactional
    public ReturnDto request(Long userId, Long orderId, ReturnRequestBody req) {
        Order order = orders.lockById(orderId).filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order not found."));
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw ApiException.conflict("Only delivered orders can be returned.");
        }
        if (order.getReturnDeadline() == null || !Instant.now().isBefore(order.getReturnDeadline())) {
            throw ApiException.conflict("The return window for this order has closed"
                    + (order.getReturnDeadline() == null ? "." : " (it ended on " + DAY.format(order.getReturnDeadline()) + ")."));
        }
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (ReturnLine line : req.items()) wanted.merge(line.orderItemId(), line.quantity(), Integer::sum);

        ReturnRequest r = new ReturnRequest(order, req.reason(), Text.clean(req.comment()));
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            OrderItem item = order.getItems().stream().filter(i -> i.getId().equals(e.getKey())).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("One of those items isn't part of this order."));
            int left = order.returnableUnits(item);
            if (e.getValue() > left) {
                throw ApiException.conflict(left == 0
                        ? item.getProductName() + " has already been returned, or is being returned."
                        : "You can return at most " + left + " of " + item.getProductName() + ".");
            }
            r.addItem(item, e.getValue());
        }
        returns.save(r);
        order.getReturns().add(r);
        int units = r.totalUnits();
        order.addEvent(OrderEventType.RETURN_REQUESTED,
                units + " item" + (units == 1 ? "" : "s") + " · " + r.getReason().label());
        notifications.returnRequested(r);
        return dto(r);
    }

    @Transactional
    public ReturnDto cancelMine(Long userId, Long orderId, Long returnId) {
        orders.lockById(orderId).filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order not found."));
        ReturnRequest r = returns.findById(returnId).filter(x -> x.getOrder().getId().equals(orderId))
                .orElseThrow(() -> ApiException.notFound("Return not found."));
        if (r.getStatus() != ReturnStatus.REQUESTED) {
            throw ApiException.conflict("The seller has already answered this request, so it can't be withdrawn.");
        }
        r.cancel();
        r.getOrder().addEvent(OrderEventType.RETURN_CANCELLED, "Withdrawn by you");
        return dto(r);
    }

    // ---------- seller and admin ----------

    @Transactional(readOnly = true)
    public PageResponse<ReturnDto> sellerList(Long sellerId, ReturnStatus status, int page, int size) {
        Page<ReturnRequest> result = status == null ? returns.findByOrderSellerId(sellerId, pageable(page, size))
                : returns.findByOrderSellerIdAndStatus(sellerId, status, pageable(page, size));
        return PageResponse.of(result, this::dto);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnDto> adminList(ReturnStatus status, int page, int size) {
        Page<ReturnRequest> result = status == null ? returns.findAll(pageable(page, size))
                : returns.findByStatus(status, pageable(page, size));
        return PageResponse.of(result, this::dto);
    }

    /** Requests waiting for an answer, for the seller's dashboard. */
    @Transactional(readOnly = true)
    public long openForSeller(Long sellerId) {
        return returns.countByOrderSellerIdAndStatus(sellerId, ReturnStatus.REQUESTED);
    }

    /** sellerId is null when an admin acts (they may act on any return). */
    @Transactional
    public ReturnDto approve(Long sellerId, Long returnId, String note) {
        ReturnRequest r = lockedReturn(sellerId, returnId);
        if (r.getStatus() != ReturnStatus.REQUESTED) throw ApiException.conflict("This request has already been answered.");
        r.approve(Text.clean(note));
        r.getOrder().addEvent(OrderEventType.RETURN_APPROVED, "Approved by " + by(sellerId));
        notifications.returnApproved(r);
        return dto(r);
    }

    @Transactional
    public ReturnDto reject(Long sellerId, Long returnId, String note) {
        String why = Text.clean(note);
        if (why == null) throw ApiException.badRequest("Please tell the customer why the return can't be accepted.");
        ReturnRequest r = lockedReturn(sellerId, returnId);
        if (r.getStatus() != ReturnStatus.REQUESTED) throw ApiException.conflict("This request has already been answered.");
        r.reject(why);
        r.getOrder().addEvent(OrderEventType.RETURN_REJECTED, "Declined by " + by(sellerId) + ": " + why);
        notifications.returnRejected(r);
        return dto(r);
    }

    /**
     * The goods are back: refund the customer. amount defaults to the most allowed and may be lowered for a partial
     * refund. Delivery is refunded only with the return that completes the whole order. If the payment provider
     * refuses, nothing here is kept: no ledger entry, no stock, the return stays approved.
     */
    @Transactional
    public ReturnDto refund(Long sellerId, Long returnId, BigDecimal requested, Boolean restock, String note) {
        ReturnRequest r = lockedReturn(sellerId, returnId);
        if (r.getStatus() != ReturnStatus.APPROVED) {
            throw ApiException.conflict(r.getStatus() == ReturnStatus.REFUNDED ? "This return has already been refunded."
                    : "A return has to be approved before it can be refunded.");
        }
        Order order = r.getOrder();
        BigDecimal max = maxRefund(r);
        BigDecimal amount = requested == null ? max : requested.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(max) > 0) {
            throw ApiException.badRequest("The refund can be at most " + Money.format(max, currency) + ".");
        }
        boolean putBack = restock == null || restock;

        BigDecimal toCard = payments.refundPart(order, amount, "pacific-refund-return-" + r.getId());
        if (putBack) {
            for (ReturnItem item : r.getItems()) {
                products.incrementStock(item.getOrderItem().getProduct().getId(), item.getQuantity());
            }
        }
        ledger.recordRefund(order, amount, amount.min(r.itemsValue()), "Return #" + r.getId() + " · order #" + order.getId());
        r.refunded(amount, putBack, Text.clean(note));
        order.addEvent(OrderEventType.REFUNDED, Money.format(amount, currency)
                + (toCard.signum() > 0 ? " refunded to the card" : " refunded by the seller"));
        notifications.returnRefunded(r, toCard.signum() > 0);
        return dto(r);
    }

    // ---------- helpers ----------

    /** The most that may be refunded for this return: the goods, plus delivery if this return completes the order. */
    private BigDecimal maxRefund(ReturnRequest r) {
        Order order = r.getOrder();
        int totalUnits = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        int alreadyRefunded = order.getReturns().stream()
                .filter(x -> x.getStatus() == ReturnStatus.REFUNDED && !x.getId().equals(r.getId()))
                .mapToInt(ReturnRequest::totalUnits).sum();
        boolean completesTheOrder = alreadyRefunded + r.totalUnits() == totalUnits;
        return completesTheOrder ? r.itemsValue().add(order.getShipping()) : r.itemsValue();
    }

    /** Locks the order, then reads the return, and checks a seller may act on it. */
    private ReturnRequest lockedReturn(Long sellerId, Long returnId) {
        Long orderId = returns.findOrderIdById(returnId).orElseThrow(() -> ApiException.notFound("Return not found."));
        Order order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Return not found."));
        if (sellerId != null && (order.getSeller() == null || !order.getSeller().getId().equals(sellerId))) {
            throw ApiException.notFound("Return not found.");
        }
        return returns.findById(returnId).orElseThrow(() -> ApiException.notFound("Return not found."));
    }

    private static String by(Long sellerId) {
        return sellerId == null ? "Pacific" : "the seller";
    }

    private ReturnDto dto(ReturnRequest r) {
        return ReturnDto.from(r, r.getStatus() == ReturnStatus.APPROVED ? maxRefund(r) : null);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
