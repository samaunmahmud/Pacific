package com.pacific.marketplace.web;

import com.pacific.marketplace.service.SellerReviewService;
import com.pacific.marketplace.web.dto.SellerDtos.SellerRatingRequest;
import com.pacific.marketplace.web.dto.SellerDtos.SellerReviewDto;
import com.pacific.marketplace.web.dto.SellerDtos.SellerStorePage;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public seller storefront pages and buyer ratings of sellers. */
@RestController
@RequestMapping("/api/sellers")
public class SellerStoreController {

    private final SellerReviewService service;

    public SellerStoreController(SellerReviewService service) {
        this.service = service;
    }

    @GetMapping("/{slug}")
    public SellerStorePage store(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return service.page(slug, CurrentUser.customerIdOrNull(jwt));
    }

    @PutMapping("/{slug}/rating")
    public SellerReviewDto rate(@PathVariable String slug, @Valid @RequestBody SellerRatingRequest req,
                                @AuthenticationPrincipal Jwt jwt) {
        return service.rate(CurrentUser.id(jwt), slug, req);
    }

    @DeleteMapping("/{slug}/rating")
    public ResponseEntity<Void> deleteRating(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        service.deleteMine(CurrentUser.id(jwt), slug);
        return ResponseEntity.noContent().build();
    }
}
