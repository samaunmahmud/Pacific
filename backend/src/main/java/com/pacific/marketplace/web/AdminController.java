package com.pacific.marketplace.web;

import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.ReviewStatus;
import com.pacific.marketplace.domain.SellerStatus;
import com.pacific.marketplace.service.AdminStatsService;
import com.pacific.marketplace.service.LedgerService;
import com.pacific.marketplace.service.OrderService;
import com.pacific.marketplace.service.ProductService;
import com.pacific.marketplace.service.ReviewService;
import com.pacific.marketplace.service.SellerReviewService;
import com.pacific.marketplace.service.SellerService;
import com.pacific.marketplace.service.SettingsService;
import com.pacific.marketplace.web.dto.OrderDtos.OrderDto;
import com.pacific.marketplace.web.dto.OrderDtos.StatusRequest;
import com.pacific.marketplace.domain.ReturnStatus;
import com.pacific.marketplace.notify.NotificationService;
import com.pacific.marketplace.service.ReturnService;
import com.pacific.marketplace.web.dto.ReturnDtos.DecisionRequest;
import com.pacific.marketplace.web.dto.ReturnDtos.RefundRequest;
import com.pacific.marketplace.web.dto.ReturnDtos.ReturnDto;
import com.pacific.marketplace.web.dto.EmailDtos.SentEmailDto;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.ProductDtos.CategoryDto;
import com.pacific.marketplace.web.dto.ProductDtos.CategoryRequest;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import com.pacific.marketplace.web.dto.ProductDtos.ProductRequest;
import com.pacific.marketplace.web.dto.ProductDtos.StockRequest;
import com.pacific.marketplace.web.dto.ReviewDtos.AdminReviewDto;
import com.pacific.marketplace.web.dto.ReviewDtos.ModerationEditRequest;
import com.pacific.marketplace.web.dto.ReviewDtos.ReportRequest;
import com.pacific.marketplace.web.dto.SellerDtos.AdminSellerDto;
import com.pacific.marketplace.web.dto.SellerDtos.CommissionRequest;
import com.pacific.marketplace.web.dto.SellerDtos.DefaultCommissionRequest;
import com.pacific.marketplace.web.dto.SellerDtos.LedgerEntryDto;
import com.pacific.marketplace.web.dto.SellerDtos.PayoutRequest;
import com.pacific.marketplace.web.dto.SellerDtos.SellerStatusRequest;
import java.math.BigDecimal;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Everything under /api/admin requires the ADMIN role (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ProductService products;
    private final OrderService orders;
    private final ReviewService reviews;
    private final AdminStatsService stats;
    private final SellerService sellers;
    private final LedgerService ledger;
    private final SettingsService settings;
    private final SellerReviewService sellerReviews;
    private final NotificationService notifications;
    private final ReturnService returns;

    public AdminController(ProductService products, OrderService orders, ReviewService reviews,
                           AdminStatsService stats, SellerService sellers, LedgerService ledger,
                           SettingsService settings, SellerReviewService sellerReviews,
                           NotificationService notifications, ReturnService returns) {
        this.products = products;
        this.orders = orders;
        this.reviews = reviews;
        this.stats = stats;
        this.sellers = sellers;
        this.ledger = ledger;
        this.settings = settings;
        this.sellerReviews = sellerReviews;
        this.notifications = notifications;
        this.returns = returns;
    }

    @GetMapping("/stats")
    public AdminStatsService.Stats stats() {
        return stats.stats();
    }

    // ----- products & categories -----

    @GetMapping("/products")
    public PageResponse<ProductDto> products(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(defaultValue = "newest") String sort,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return products.adminSearch(q, category, sort, page, size);
    }

    @GetMapping("/products/{id}")
    public ProductDto product(@PathVariable Long id) {
        return products.adminGet(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto createProduct(@Valid @RequestBody ProductRequest req) {
        return products.create(req);
    }

    @PutMapping("/products/{id}")
    public ProductDto updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return products.update(id, req);
    }

    @PatchMapping("/products/{id}/stock")
    public ProductDto setStock(@PathVariable Long id, @Valid @RequestBody StockRequest req) {
        return products.setStock(id, req.stock());
    }

    /** Hides the product from the storefront (it stays in past orders). Restore it with PUT active=true. */
    @DeleteMapping("/products/{id}")
    public ProductDto deactivateProduct(@PathVariable Long id) {
        return products.deactivate(id);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest req) {
        return products.createCategory(req.name());
    }

    @PutMapping("/categories/{id}")
    public CategoryDto renameCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return products.renameCategory(id, req.name());
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        products.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ----- orders -----

    @GetMapping("/orders")
    public PageResponse<OrderDto> orders(@RequestParam(required = false) OrderStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return orders.adminList(status, page, size);
    }

    @GetMapping("/returns")
    public PageResponse<ReturnDto> returns(@RequestParam(required = false) ReturnStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return returns.adminList(status, page, size);
    }

    @PostMapping("/returns/{id}/approve")
    public ReturnDto approveReturn(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest req) {
        return returns.approve(null, id, req == null ? null : req.note());
    }

    @PostMapping("/returns/{id}/reject")
    public ReturnDto rejectReturn(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest req) {
        return returns.reject(null, id, req == null ? null : req.note());
    }

    @PostMapping("/returns/{id}/refund")
    public ReturnDto refundReturn(@PathVariable Long id, @Valid @RequestBody(required = false) RefundRequest req) {
        return returns.refund(null, id, req == null ? null : req.amount(), req == null ? null : req.restock(),
                req == null ? null : req.note());
    }

    /** Every email the shop has sent (or, with no mail server, would have sent). */
    @GetMapping("/emails")
    public PageResponse<SentEmailDto> emails(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return notifications.log(page, size);
    }

    @GetMapping("/orders/{id}")
    public OrderDto order(@PathVariable Long id) {
        return orders.adminGet(id);
    }

    @PatchMapping("/orders/{id}/status")
    public OrderDto setOrderStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return orders.adminSetStatus(id, req.status(), req.carrier(), req.trackingNumber());
    }

    // ----- review moderation -----

    @GetMapping("/reviews")
    public PageResponse<AdminReviewDto> reviews(@RequestParam(required = false) ReviewStatus status,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return reviews.adminList(status, q, page, size);
    }

    @GetMapping("/reviews/{id}")
    public AdminReviewDto review(@PathVariable Long id) {
        return reviews.adminGet(id);
    }

    @PutMapping("/reviews/{id}")
    public AdminReviewDto editReview(@PathVariable Long id, @Valid @RequestBody ModerationEditRequest req) {
        return reviews.adminEdit(id, req);
    }

    @PostMapping("/reviews/{id}/dismiss")
    public AdminReviewDto dismissFlag(@PathVariable Long id) {
        return reviews.adminDismissFlag(id);
    }

    @PostMapping("/reviews/{id}/flag")
    public AdminReviewDto flag(@PathVariable Long id, @Valid @RequestBody(required = false) ReportRequest req) {
        return reviews.adminFlag(id, req == null ? null : req.reason());
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviews.adminDelete(id);
        return ResponseEntity.noContent().build();
    }

    // ----- sellers, commission & payouts -----

    @GetMapping("/sellers")
    public PageResponse<AdminSellerDto> sellers(@RequestParam(required = false) SellerStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return sellers.adminList(status, page, size);
    }

    @GetMapping("/sellers/{id}")
    public AdminSellerDto seller(@PathVariable Long id) {
        return sellers.adminGet(id);
    }

    @PatchMapping("/sellers/{id}/status")
    public AdminSellerDto setSellerStatus(@PathVariable Long id, @Valid @RequestBody SellerStatusRequest req) {
        return sellers.adminSetStatus(id, req.status(), req.note());
    }

    @PutMapping("/sellers/{id}/commission")
    public AdminSellerDto setSellerCommission(@PathVariable Long id, @Valid @RequestBody CommissionRequest req) {
        return sellers.adminSetCommission(id, req.percent());
    }

    @GetMapping("/sellers/{id}/ledger")
    public PageResponse<LedgerEntryDto> sellerLedger(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ledger.entries(id, page, size);
    }

    @PostMapping("/sellers/{id}/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryDto recordPayout(@PathVariable Long id, @Valid @RequestBody PayoutRequest req) {
        return ledger.payout(id, req.amount(), req.note());
    }

    @GetMapping("/settings/commission")
    public Map<String, BigDecimal> defaultCommission() {
        return Map.of("percent", settings.defaultCommission());
    }

    @PutMapping("/settings/commission")
    public Map<String, BigDecimal> setDefaultCommission(@Valid @RequestBody DefaultCommissionRequest req) {
        return Map.of("percent", settings.setDefaultCommission(req.percent()));
    }

    @DeleteMapping("/seller-ratings/{id}")
    public ResponseEntity<Void> deleteSellerRating(@PathVariable Long id) {
        sellerReviews.adminDelete(id);
        return ResponseEntity.noContent().build();
    }
}
