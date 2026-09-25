package com.pacific.marketplace.web;

import com.pacific.marketplace.service.Recommendations;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Search suggestions, product-page recommendations and "Buy it again". */
@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final Recommendations recommendations;

    public RecommendationController(Recommendations recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping("/search/suggest")
    public Recommendations.Suggestions suggest(@RequestParam(required = false) String q) {
        return recommendations.suggest(q);
    }

    @GetMapping("/products/{id}/recommendations")
    public Recommendations.ForProduct forProduct(@PathVariable Long id) {
        return recommendations.forProduct(id);
    }

    @GetMapping("/me/buy-again")
    public List<ProductDto> buyAgain(@AuthenticationPrincipal Jwt jwt) {
        return recommendations.buyAgain(CurrentUser.id(jwt));
    }
}
