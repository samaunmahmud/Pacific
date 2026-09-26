package com.pacific.marketplace.web;

import com.pacific.marketplace.service.SellerService;
import com.pacific.marketplace.service.VariationService;
import com.pacific.marketplace.web.dto.ProductDtos.FamilyRequest;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import com.pacific.marketplace.web.dto.ProductDtos.VariationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Managing a product's variations: sellers for their own products, admins for any (Pacific's own included). The
 * variations themselves appear on the product (see ProductDto.variations).
 */
@RestController
public class VariationController {

    private final VariationService variations;
    private final SellerService sellers;

    public VariationController(VariationService variations, SellerService sellers) {
        this.variations = variations;
        this.sellers = sellers;
    }

    @PutMapping("/api/seller/products/{id}/family")
    public ProductDto sellerSetFamily(@PathVariable Long id, @Valid @RequestBody FamilyRequest req,
                                      @AuthenticationPrincipal Jwt jwt) {
        return variations.setFamily(id, sellerId(jwt), req);
    }

    @PostMapping("/api/seller/products/{id}/variations")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto sellerAdd(@PathVariable Long id, @Valid @RequestBody VariationRequest req,
                                @AuthenticationPrincipal Jwt jwt) {
        return variations.addVariation(id, sellerId(jwt), req);
    }

    @DeleteMapping("/api/seller/products/{id}/family")
    public ProductDto sellerLeave(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return variations.leave(id, sellerId(jwt));
    }

    @PutMapping("/api/admin/products/{id}/family")
    public ProductDto adminSetFamily(@PathVariable Long id, @Valid @RequestBody FamilyRequest req) {
        return variations.setFamily(id, null, req);
    }

    @PostMapping("/api/admin/products/{id}/variations")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto adminAdd(@PathVariable Long id, @Valid @RequestBody VariationRequest req) {
        return variations.addVariation(id, null, req);
    }

    @DeleteMapping("/api/admin/products/{id}/family")
    public ProductDto adminLeave(@PathVariable Long id) {
        return variations.leave(id, null);
    }

    private Long sellerId(Jwt jwt) {
        return sellers.approved(CurrentUser.id(jwt)).getId();
    }
}
