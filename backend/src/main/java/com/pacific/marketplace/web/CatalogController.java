package com.pacific.marketplace.web;

import com.pacific.marketplace.service.OfferService;
import com.pacific.marketplace.service.ProductService;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.ProductDtos.CategoryDto;
import com.pacific.marketplace.web.dto.ProductDtos.OfferDto;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final ProductService products;
    private final OfferService offers;

    public CatalogController(ProductService products, OfferService offers) {
        this.products = products;
        this.offers = offers;
    }

    @GetMapping("/products")
    public PageResponse<ProductDto> products(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) String seller,
                                             @RequestParam(defaultValue = "false") boolean deals,
                                             @RequestParam(required = false) BigDecimal minPrice,
                                             @RequestParam(required = false) BigDecimal maxPrice,
                                             @RequestParam(required = false) Double minRating,
                                             @RequestParam(defaultValue = "newest") String sort,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "12") int size) {
        return products.search(q, category, seller, deals, new ProductService.Filters(minPrice, maxPrice, minRating),
                sort, page, size);
    }

    /** Used by the "recently viewed" strip (the ids live in the visitor's browser). */
    @GetMapping("/products/batch")
    public List<ProductDto> batch(@RequestParam List<Long> ids) {
        return products.batch(ids);
    }

    @GetMapping("/products/{id}")
    public ProductDto product(@PathVariable Long id) {
        return products.get(id);
    }

    /** Every seller's offer for this product, the buy box first. */
    @GetMapping("/products/{id}/offers")
    public List<OfferDto> offers(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return offers.offers(id, CurrentUser.customerIdOrNull(jwt));
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return products.categories();
    }
}
