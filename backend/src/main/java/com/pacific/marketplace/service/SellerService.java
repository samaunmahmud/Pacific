package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.SellerStatus;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.repo.OrderItemRepository;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.domain.ReturnStatus;
import com.pacific.marketplace.repo.QuestionRepository;
import com.pacific.marketplace.repo.ReturnRequestRepository;
import com.pacific.marketplace.repo.SellerProfileRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.SellerDtos.AdminSellerDto;
import com.pacific.marketplace.web.dto.SellerDtos.ApplyRequest;
import com.pacific.marketplace.web.dto.SellerDtos.LowStock;
import com.pacific.marketplace.web.dto.SellerDtos.SellerDto;
import com.pacific.marketplace.web.dto.SellerDtos.SellerStats;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final SellerProfileRepository sellers;
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final QuestionRepository questions;
    private final LedgerService ledger;
    private final SettingsService settings;
    private final ReturnRequestRepository returns;

    public SellerService(SellerProfileRepository sellers, UserRepository users, ProductRepository products,
                         OrderRepository orders, OrderItemRepository orderItems, QuestionRepository questions,
                         LedgerService ledger, SettingsService settings, ReturnRequestRepository returns) {
        this.sellers = sellers;
        this.users = users;
        this.products = products;
        this.orders = orders;
        this.orderItems = orderItems;
        this.questions = questions;
        this.ledger = ledger;
        this.settings = settings;
        this.returns = returns;
    }

    // ---------- onboarding ----------

    /** A customer applies to sell. A rejected applicant may apply again with changes. */
    @Transactional
    public SellerDto apply(Long userId, ApplyRequest req) {
        String name = req.storeName().strip();
        SellerProfile existing = sellers.findByUserId(userId).orElse(null);
        if (existing != null && existing.getStatus() != SellerStatus.REJECTED) {
            throw ApiException.conflict("You've already applied to sell on Pacific.");
        }
        checkNameFree(name, existing);
        try {
            if (existing != null) {
                existing.updateStore(name, Text.clean(req.description()));
                existing.setStatus(SellerStatus.PENDING, null);
                sellers.flush();
                return dto(existing);
            }
            User user = users.getReferenceById(userId);
            SellerProfile profile = new SellerProfile(user, name, uniqueSlug(name), Text.clean(req.description()));
            return dto(sellers.saveAndFlush(profile));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("That store name is already taken.");
        }
    }

    /** The caller's seller profile, or empty if they haven't applied. */
    @Transactional(readOnly = true)
    public java.util.Optional<SellerDto> me(Long userId) {
        return sellers.findByUserId(userId).map(this::dto);
    }

    @Transactional
    public SellerDto updateStore(Long userId, ApplyRequest req) {
        SellerProfile seller = sellers.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("You haven't applied to sell yet."));
        String name = req.storeName().strip();
        checkNameFree(name, seller);
        seller.updateStore(name, Text.clean(req.description())); // the URL slug stays stable
        sellers.flush();
        return dto(seller);
    }

    /** The caller's profile, but only if they're an approved seller; explains why not otherwise. */
    @Transactional(readOnly = true)
    public SellerProfile approved(Long userId) {
        SellerProfile seller = sellers.findByUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("You need a seller account to do that."));
        return switch (seller.getStatus()) {
            case APPROVED -> seller;
            case PENDING -> throw ApiException.forbidden("Your seller application is still awaiting approval.");
            case REJECTED -> throw ApiException.forbidden("Your seller application was not approved.");
            case SUSPENDED -> throw ApiException.forbidden("Your seller account is suspended.");
        };
    }

    // ---------- seller dashboard ----------

    @Transactional(readOnly = true)
    public SellerStats stats(Long sellerId) {
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        long total = 0;
        for (OrderStatus s : OrderStatus.values()) {
            if (s == OrderStatus.AWAITING_PAYMENT) continue; // unpaid card orders aren't the seller's business yet
            long n = orders.countBySellerIdAndStatus(sellerId, s);
            byStatus.put(s, n);
            total += n;
        }
        List<LowStock> low = products
                .findBySellerIdAndActiveTrueAndStockLessThanEqualOrderByStockAscNameAsc(sellerId, LOW_STOCK_THRESHOLD)
                .stream().map(p -> new LowStock(p.getId(), p.getName(), p.getStock())).toList();
        return new SellerStats(orders.grossSalesForSeller(sellerId), total, byStatus,
                orderItems.unitsSoldBySeller(sellerId), ledger.balance(sellerId),
                questions.countUnansweredForSeller(sellerId), returns.countByOrderSellerIdAndStatus(sellerId, ReturnStatus.REQUESTED),
                products.countBySellerId(sellerId), low,
                LOW_STOCK_THRESHOLD);
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<AdminSellerDto> adminList(SellerStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        var result = status == null ? sellers.findAll(pageable) : sellers.findByStatus(status, pageable);
        return PageResponse.of(result, this::adminDto);
    }

    @Transactional(readOnly = true)
    public AdminSellerDto adminGet(Long id) {
        return adminDto(sellers.findWithUserById(id).orElseThrow(() -> ApiException.notFound("Seller not found.")));
    }

    /** Approve, reject or suspend. A suspended seller's products disappear from the store until reinstated. */
    @Transactional
    public AdminSellerDto adminSetStatus(Long id, SellerStatus status, String note) {
        if (status == SellerStatus.PENDING) throw ApiException.badRequest("A seller can't be moved back to pending.");
        SellerProfile seller = sellers.findWithUserById(id).orElseThrow(() -> ApiException.notFound("Seller not found."));
        seller.setStatus(status, Text.clean(note));
        sellers.flush();
        return adminDto(seller);
    }

    /** {@code percent} null clears the override so the marketplace default applies. */
    @Transactional
    public AdminSellerDto adminSetCommission(Long id, BigDecimal percent) {
        SellerProfile seller = sellers.findWithUserById(id).orElseThrow(() -> ApiException.notFound("Seller not found."));
        seller.setCommissionOverride(percent == null ? null : percent.setScale(2, java.math.RoundingMode.HALF_UP));
        sellers.flush();
        return adminDto(seller);
    }

    // ---------- helpers ----------

    private SellerDto dto(SellerProfile s) {
        return SellerDto.from(s, settings.effectiveCommission(s));
    }

    private AdminSellerDto adminDto(SellerProfile s) {
        return new AdminSellerDto(dto(s), s.getUser().getName(), s.getUser().getEmail(), ledger.balance(s.getId()),
                products.countBySellerId(s.getId()));
    }

    private void checkNameFree(String name, SellerProfile self) {
        boolean taken = sellers.existsByStoreNameIgnoreCase(name)
                && (self == null || !self.getStoreName().equalsIgnoreCase(name));
        if (taken || "pacific".equalsIgnoreCase(name)) throw ApiException.conflict("That store name is already taken.");
    }

    private String uniqueSlug(String name) {
        String base = Text.slugify(name);
        String slug = base;
        int n = 2;
        while (sellers.existsBySlug(slug)) slug = base + "-" + n++;
        return slug;
    }
}
