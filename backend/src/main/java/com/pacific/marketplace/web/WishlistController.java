package com.pacific.marketplace.web;

import com.pacific.marketplace.service.WishlistService;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlist;

    public WishlistController(WishlistService wishlist) {
        this.wishlist = wishlist;
    }

    @GetMapping
    public List<ProductDto> list(@AuthenticationPrincipal Jwt jwt) {
        return wishlist.list(CurrentUser.id(jwt));
    }

    @GetMapping("/ids")
    public List<Long> ids(@AuthenticationPrincipal Jwt jwt) {
        return wishlist.ids(CurrentUser.id(jwt));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> add(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
        wishlist.add(CurrentUser.id(jwt), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
        wishlist.remove(CurrentUser.id(jwt), productId);
        return ResponseEntity.noContent().build();
    }
}
