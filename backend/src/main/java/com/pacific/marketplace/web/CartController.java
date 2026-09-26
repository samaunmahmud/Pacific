package com.pacific.marketplace.web;

import com.pacific.marketplace.service.CartService;
import com.pacific.marketplace.web.dto.CartDtos.AddToCartRequest;
import com.pacific.marketplace.web.dto.CartDtos.CartDto;
import com.pacific.marketplace.web.dto.CartDtos.UpdateCartRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public CartDto get(@AuthenticationPrincipal Jwt jwt) {
        return cart.get(CurrentUser.id(jwt));
    }

    @PostMapping("/items")
    public CartDto add(@Valid @RequestBody AddToCartRequest req, @AuthenticationPrincipal Jwt jwt) {
        return cart.add(CurrentUser.id(jwt), req.productId(), req.quantity());
    }

    @PatchMapping("/items/{productId}")
    public CartDto update(@PathVariable Long productId, @Valid @RequestBody UpdateCartRequest req,
                          @AuthenticationPrincipal Jwt jwt) {
        return cart.setQuantity(CurrentUser.id(jwt), productId, req.quantity());
    }

    /** Keeps the item in the cart but out of the totals and checkout. */
    @PostMapping("/items/{productId}/save-for-later")
    public CartDto saveForLater(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
        return cart.saveForLater(CurrentUser.id(jwt), productId, true);
    }

    @PostMapping("/items/{productId}/move-to-cart")
    public CartDto moveToCart(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
        return cart.saveForLater(CurrentUser.id(jwt), productId, false);
    }

    @DeleteMapping("/items/{productId}")
    public CartDto remove(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
        return cart.remove(CurrentUser.id(jwt), productId);
    }
}
