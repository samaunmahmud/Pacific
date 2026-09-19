package com.pacific.marketplace.web;

import com.pacific.marketplace.service.CheckoutService;
import com.pacific.marketplace.service.OrderService;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutRequest;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutResponse;
import com.pacific.marketplace.web.dto.OrderDtos.OrderDto;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;
    private final CheckoutService checkouts;

    public OrderController(OrderService orders, CheckoutService checkouts) {
        this.orders = orders;
        this.checkouts = checkouts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest req, @AuthenticationPrincipal Jwt jwt) {
        return checkouts.checkout(CurrentUser.id(jwt), req);
    }

    @GetMapping
    public List<OrderDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return orders.myOrders(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return orders.myOrder(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderDto cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return orders.cancelMine(CurrentUser.id(jwt), id);
    }
}
