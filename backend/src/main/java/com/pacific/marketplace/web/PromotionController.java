package com.pacific.marketplace.web;

import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.service.Promotions;
import com.pacific.marketplace.service.SellerService;
import com.pacific.marketplace.web.dto.PromotionDtos.CodeRequest;
import com.pacific.marketplace.web.dto.PromotionDtos.CouponRequest;
import com.pacific.marketplace.web.dto.PromotionDtos.DealRequest;
import com.pacific.marketplace.web.dto.PromotionDtos.StoreCodeDto;
import com.pacific.marketplace.web.dto.PromotionDtos.StoreCouponDto;
import com.pacific.marketplace.web.dto.PromotionDtos.StoreDealDto;
import com.pacific.marketplace.web.dto.PromotionDtos.StorePromotionsDto;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightning Deals, coupons and promo codes. Sellers run them on their own listings under /api/seller/promotions;
 * admins run them on Pacific's own products under /api/admin/promotions. Shoppers clip coupons.
 */
@RestController
@RequestMapping("/api")
public class PromotionController {

    private final Promotions promotions;
    private final SellerService sellers;

    public PromotionController(Promotions promotions, SellerService sellers) {
        this.promotions = promotions;
        this.sellers = sellers;
    }

    /** "Apply coupon" on a product page. */
    @PostMapping("/coupons/{id}/clip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clip(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        promotions.clip(CurrentUser.id(jwt), id);
    }

    // ---------- sellers: their own listings ----------

    private SellerProfile seller(Jwt jwt) {
        return sellers.approved(CurrentUser.id(jwt));
    }

    @GetMapping("/seller/promotions")
    public StorePromotionsDto sellerList(@AuthenticationPrincipal Jwt jwt) {
        return list(seller(jwt).getId());
    }

    @PostMapping("/seller/promotions/deals")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreDealDto sellerDeal(@Valid @RequestBody DealRequest req, @AuthenticationPrincipal Jwt jwt) {
        return deal(seller(jwt).getId(), req);
    }

    @PostMapping("/seller/promotions/deals/{id}/end")
    public StoreDealDto sellerEndDeal(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return StoreDealDto.from(promotions.endDeal(seller(jwt).getId(), id), Instant.now());
    }

    @PostMapping("/seller/promotions/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreCouponDto sellerCoupon(@Valid @RequestBody CouponRequest req, @AuthenticationPrincipal Jwt jwt) {
        return coupon(seller(jwt).getId(), req);
    }

    @PostMapping("/seller/promotions/coupons/{id}/stop")
    public StoreCouponDto sellerStopCoupon(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return StoreCouponDto.from(promotions.stopCoupon(seller(jwt).getId(), id), Instant.now());
    }

    @PostMapping("/seller/promotions/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreCodeDto sellerCode(@Valid @RequestBody CodeRequest req, @AuthenticationPrincipal Jwt jwt) {
        return code(seller(jwt), req);
    }

    @PostMapping("/seller/promotions/codes/{id}/stop")
    public StoreCodeDto sellerStopCode(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return StoreCodeDto.from(promotions.stopCode(seller(jwt).getId(), id), Instant.now());
    }

    // ---------- admins: Pacific's own products ----------

    @GetMapping("/admin/promotions")
    public StorePromotionsDto adminList() {
        return list(null);
    }

    @PostMapping("/admin/promotions/deals")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreDealDto adminDeal(@Valid @RequestBody DealRequest req) {
        return deal(null, req);
    }

    @PostMapping("/admin/promotions/deals/{id}/end")
    public StoreDealDto adminEndDeal(@PathVariable Long id) {
        return StoreDealDto.from(promotions.endDeal(null, id), Instant.now());
    }

    @PostMapping("/admin/promotions/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreCouponDto adminCoupon(@Valid @RequestBody CouponRequest req) {
        return coupon(null, req);
    }

    @PostMapping("/admin/promotions/coupons/{id}/stop")
    public StoreCouponDto adminStopCoupon(@PathVariable Long id) {
        return StoreCouponDto.from(promotions.stopCoupon(null, id), Instant.now());
    }

    @PostMapping("/admin/promotions/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreCodeDto adminCode(@Valid @RequestBody CodeRequest req) {
        return code(null, req);
    }

    @PostMapping("/admin/promotions/codes/{id}/stop")
    public StoreCodeDto adminStopCode(@PathVariable Long id) {
        return StoreCodeDto.from(promotions.stopCode(null, id), Instant.now());
    }

    // ---------- shared ----------

    private StorePromotionsDto list(Long sellerId) {
        Instant now = Instant.now();
        return new StorePromotionsDto(
                promotions.storeDeals(sellerId).stream().map(d -> StoreDealDto.from(d, now)).toList(),
                promotions.storeCoupons(sellerId).stream().map(c -> StoreCouponDto.from(c, now)).toList(),
                promotions.storeCodes(sellerId).stream().map(c -> StoreCodeDto.from(c, now)).toList());
    }

    private StoreDealDto deal(Long sellerId, DealRequest req) {
        return StoreDealDto.from(promotions.createDeal(sellerId, req.productId(), req.price(), req.quantity(),
                req.startsAt(), req.hours()), Instant.now());
    }

    private StoreCouponDto coupon(Long sellerId, CouponRequest req) {
        return StoreCouponDto.from(promotions.createCoupon(sellerId, req.productId(), req.percentOff(), req.budget(),
                req.days()), Instant.now());
    }

    private StoreCodeDto code(SellerProfile store, CodeRequest req) {
        return StoreCodeDto.from(promotions.createCode(store, req.code(), req.percentOff(), req.minSpend(), req.maxUses(),
                req.days()), Instant.now());
    }
}
