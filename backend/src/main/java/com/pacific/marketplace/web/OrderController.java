package com.pacific.marketplace.web;

import com.pacific.marketplace.service.CheckoutService;
import com.pacific.marketplace.service.OrderService;
import com.pacific.marketplace.service.ReturnService;
import com.pacific.marketplace.service.EmailVerificationService;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutRequest;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutResponse;
import com.pacific.marketplace.web.dto.OrderDtos.OrderDto;
import com.pacific.marketplace.web.dto.ReturnDtos.ReturnDto;
import com.pacific.marketplace.web.dto.ReturnDtos.ReturnRequestBody;
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
    private final ReturnService returns;
    private final EmailVerificationService verification;

    public OrderController(OrderService orders, CheckoutService checkouts, ReturnService returns,
                           EmailVerificationService verification) {
        this.orders = orders;
        this.checkouts = checkouts;
        this.returns = returns;
        this.verification = verification;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest req, @AuthenticationPrincipal Jwt jwt) {
        verification.requireConfirmed(CurrentUser.id(jwt), "place an order");
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

    /** Ask to send items back from a delivered order, within its return window. */
    @PostMapping("/{id}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnDto requestReturn(@PathVariable Long id, @Valid @RequestBody ReturnRequestBody req,
                                   @AuthenticationPrincipal Jwt jwt) {
        return returns.request(CurrentUser.id(jwt), id, req);
    }

    /** Withdraw a return request the seller hasn't answered yet. */
    @PostMapping("/{id}/returns/{returnId}/cancel")
    public ReturnDto cancelReturn(@PathVariable Long id, @PathVariable Long returnId, @AuthenticationPrincipal Jwt jwt) {
        return returns.cancelMine(CurrentUser.id(jwt), id, returnId);
    }
}
