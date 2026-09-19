package com.pacific.marketplace.web;

import com.pacific.marketplace.service.ReviewService;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import com.pacific.marketplace.web.dto.ReviewDtos.ReviewDto;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's own review dashboard. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final ReviewService reviews;

    public MeController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/reviews")
    public List<ReviewDto> myReviews(@RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "newest") String sort,
                                     @AuthenticationPrincipal Jwt jwt) {
        return reviews.mine(CurrentUser.id(jwt), q, sort);
    }

    @GetMapping("/unrated-products")
    public List<ProductDto> unrated(@AuthenticationPrincipal Jwt jwt) {
        return reviews.unratedPurchases(CurrentUser.id(jwt));
    }
}
