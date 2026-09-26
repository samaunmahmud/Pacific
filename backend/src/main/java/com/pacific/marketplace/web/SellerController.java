package com.pacific.marketplace.web;

import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.ReturnStatus;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.service.EmailVerificationService;
import com.pacific.marketplace.service.LedgerService;
import com.pacific.marketplace.service.OfferService;
import com.pacific.marketplace.service.OrderService;
import com.pacific.marketplace.service.ProductService;
import com.pacific.marketplace.service.QaService;
import com.pacific.marketplace.service.ReturnService;
import com.pacific.marketplace.service.SellerService;
import com.pacific.marketplace.web.dto.OrderDtos.OrderDto;
import com.pacific.marketplace.web.dto.OrderDtos.StatusRequest;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.ProductDtos.OfferRequest;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import com.pacific.marketplace.web.dto.ProductDtos.ProductRequest;
import com.pacific.marketplace.web.dto.ProductDtos.StockRequest;
import com.pacific.marketplace.web.dto.QaDtos.SellerQuestionDto;
import com.pacific.marketplace.web.dto.ReturnDtos.DecisionRequest;
import com.pacific.marketplace.web.dto.ReturnDtos.RefundRequest;
import com.pacific.marketplace.web.dto.ReturnDtos.ReturnDto;
import com.pacific.marketplace.web.dto.SellerDtos.ApplyRequest;
import com.pacific.marketplace.web.dto.SellerDtos.DeliverySettingsRequest;
import com.pacific.marketplace.web.dto.SellerDtos.EarningsDto;
import com.pacific.marketplace.web.dto.SellerDtos.SellerDto;
import com.pacific.marketplace.web.dto.SellerDtos.SellerStats;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

/**
 * Seller Central. Any customer can apply; everything else needs an APPROVED seller profile
 * (checked by {@link SellerService#approved} on every call, so a suspension takes effect immediately).
 */
@RestController
@RequestMapping("/api/seller")
public class SellerController {

    private final SellerService sellers;
    private final ProductService products;
    private final OrderService orders;
    private final LedgerService ledger;
    private final QaService qa;
    private final ReturnService returns;
    private final OfferService offers;
    private final EmailVerificationService verification;

    public SellerController(SellerService sellers, ProductService products, OrderService orders, LedgerService ledger,
                            QaService qa, ReturnService returns,
                            EmailVerificationService verification,
                            OfferService offers) {
        this.sellers = sellers;
        this.products = products;
        this.orders = orders;
        this.ledger = ledger;
        this.qa = qa;
        this.returns = returns;
        this.offers = offers;
        this.verification = verification;
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public SellerDto apply(@Valid @RequestBody ApplyRequest req, @AuthenticationPrincipal Jwt jwt) {
        verification.requireConfirmed(CurrentUser.id(jwt), "sell on Pacific");
        return sellers.apply(CurrentUser.id(jwt), req);
    }

    /** 200 with the profile, or 204 if this customer hasn't applied (not an error, so it isn't a 404). */
    @GetMapping("/me")
    public ResponseEntity<SellerDto> me(@AuthenticationPrincipal Jwt jwt) {
        return sellers.me(CurrentUser.id(jwt)).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/me")
    public SellerDto updateStore(@Valid @RequestBody ApplyRequest req, @AuthenticationPrincipal Jwt jwt) {
        return sellers.updateStore(CurrentUser.id(jwt), req);
    }

    @PutMapping("/me/delivery")
    public SellerDto updateDelivery(@Valid @RequestBody DeliverySettingsRequest req, @AuthenticationPrincipal Jwt jwt) {
        return sellers.updateDelivery(CurrentUser.id(jwt), req.freeDeliveryThreshold(), req.dispatchDays());
    }

    @GetMapping("/stats")
    public SellerStats stats(@AuthenticationPrincipal Jwt jwt) {
        return sellers.stats(seller(jwt).getId());
    }

    // ----- products -----

    @GetMapping("/products")
    public PageResponse<ProductDto> products(@RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @AuthenticationPrincipal Jwt jwt) {
        return products.sellerSearch(seller(jwt).getId(), q, page, size);
    }

    @GetMapping("/products/{id}")
    public ProductDto product(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return products.sellerGet(seller(jwt).getId(), id);
    }

    /** Start selling a product that's already in the catalog (any of its listings' ids). */
    @PostMapping("/offers/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto addOffer(@PathVariable Long productId, @Valid @RequestBody OfferRequest req,
                               @AuthenticationPrincipal Jwt jwt) {
        return offers.addOffer(CurrentUser.id(jwt), productId, req);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto createProduct(@Valid @RequestBody ProductRequest req, @AuthenticationPrincipal Jwt jwt) {
        return products.sellerCreate(seller(jwt), req);
    }

    @PutMapping("/products/{id}")
    public ProductDto updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        return products.sellerUpdate(seller(jwt).getId(), id, req);
    }

    @PatchMapping("/products/{id}/stock")
    public ProductDto setStock(@PathVariable Long id, @Valid @RequestBody StockRequest req,
                               @AuthenticationPrincipal Jwt jwt) {
        return products.sellerSetStock(seller(jwt).getId(), id, req.stock());
    }

    @DeleteMapping("/products/{id}")
    public ProductDto deactivateProduct(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return products.sellerDeactivate(seller(jwt).getId(), id);
    }

    // ----- orders -----

    @GetMapping("/orders")
    public PageResponse<OrderDto> orders(@RequestParam(required = false) OrderStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @AuthenticationPrincipal Jwt jwt) {
        return orders.sellerList(seller(jwt).getId(), status, page, size);
    }

    @GetMapping("/orders/{id}")
    public OrderDto order(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return orders.sellerGet(seller(jwt).getId(), id);
    }

    @PatchMapping("/orders/{id}/status")
    public OrderDto setOrderStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req,
                                   @AuthenticationPrincipal Jwt jwt) {
        return orders.sellerSetStatus(seller(jwt).getId(), id, req.status(), req.carrier(), req.trackingNumber());
    }

    // ----- returns -----

    @GetMapping("/returns")
    public PageResponse<ReturnDto> returns(@RequestParam(required = false) ReturnStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        return returns.sellerList(seller(jwt).getId(), status, page, size);
    }

    @PostMapping("/returns/{id}/approve")
    public ReturnDto approveReturn(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest req,
                                   @AuthenticationPrincipal Jwt jwt) {
        return returns.approve(seller(jwt).getId(), id, req == null ? null : req.note());
    }

    @PostMapping("/returns/{id}/reject")
    public ReturnDto rejectReturn(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest req,
                                  @AuthenticationPrincipal Jwt jwt) {
        return returns.reject(seller(jwt).getId(), id, req == null ? null : req.note());
    }

    @PostMapping("/returns/{id}/refund")
    public ReturnDto refundReturn(@PathVariable Long id, @Valid @RequestBody(required = false) RefundRequest req,
                                  @AuthenticationPrincipal Jwt jwt) {
        return returns.refund(seller(jwt).getId(), id, req == null ? null : req.amount(),
                req == null ? null : req.restock(), req == null ? null : req.note());
    }

    // ----- earnings & questions -----

    @GetMapping("/earnings")
    public EarningsDto earnings(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                @AuthenticationPrincipal Jwt jwt) {
        return ledger.earnings(seller(jwt).getId(), page, size);
    }

    @GetMapping("/questions")
    public List<SellerQuestionDto> questions(@AuthenticationPrincipal Jwt jwt) {
        return qa.unansweredForSeller(seller(jwt).getId());
    }

    private SellerProfile seller(Jwt jwt) {
        return sellers.approved(CurrentUser.id(jwt));
    }
}
