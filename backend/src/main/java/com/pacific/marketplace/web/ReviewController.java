package com.pacific.marketplace.web;

import com.pacific.marketplace.service.ReviewService;
import com.pacific.marketplace.web.dto.ReviewDtos.ProductReviewsResponse;
import com.pacific.marketplace.web.dto.ReviewDtos.ReportRequest;
import com.pacific.marketplace.web.dto.ReviewDtos.ReviewDto;
import com.pacific.marketplace.web.dto.ReviewDtos.ReviewRequest;
import com.pacific.marketplace.web.dto.ReviewDtos.VoteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    /** Public. If a customer token is sent, the response also carries their own votes and review. */
    @GetMapping("/products/{productId}/reviews")
    public ProductReviewsResponse forProduct(@PathVariable Long productId,
                                             @RequestParam(defaultValue = "newest") String sort,
                                             @AuthenticationPrincipal Jwt jwt) {
        return reviews.forProduct(productId, sort, CurrentUser.customerIdOrNull(jwt));
    }

    @PostMapping("/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto create(@PathVariable Long productId, @Valid @RequestBody ReviewRequest req,
                            @AuthenticationPrincipal Jwt jwt) {
        return reviews.create(CurrentUser.id(jwt), productId, req);
    }

    @PutMapping("/reviews/{id}")
    public ReviewDto update(@PathVariable Long id, @Valid @RequestBody ReviewRequest req,
                            @AuthenticationPrincipal Jwt jwt) {
        return reviews.update(CurrentUser.id(jwt), id, req);
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        reviews.delete(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Casting the same vote twice withdraws it. */
    @PostMapping("/reviews/{id}/vote")
    public ReviewDto vote(@PathVariable Long id, @Valid @RequestBody VoteRequest req,
                          @AuthenticationPrincipal Jwt jwt) {
        return reviews.vote(CurrentUser.id(jwt), id, req.type());
    }

    @PostMapping("/reviews/{id}/report")
    public ResponseEntity<Void> report(@PathVariable Long id, @Valid @RequestBody(required = false) ReportRequest req,
                                       @AuthenticationPrincipal Jwt jwt) {
        reviews.report(CurrentUser.id(jwt), id, req == null ? null : req.reason());
        return ResponseEntity.noContent().build();
    }
}
