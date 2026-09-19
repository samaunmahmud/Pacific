package com.pacific.marketplace.service;

import com.pacific.marketplace.web.dto.OrderDtos.CheckoutRequest;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutResponse;
import org.springframework.stereotype.Service;

/**
 * Places the orders, then (for card payments) opens the provider's payment page. Deliberately not transactional:
 * the orders and the pending payment must be committed before the provider is contacted, so a slow or failed
 * provider call never holds database locks or undoes the reservation bookkeeping.
 */
@Service
public class CheckoutService {

    private final OrderService orders;
    private final PaymentService payments;

    public CheckoutService(OrderService orders, PaymentService payments) {
        this.orders = orders;
        this.payments = payments;
    }

    public CheckoutResponse checkout(Long userId, CheckoutRequest req) {
        CheckoutResponse placed = orders.checkout(userId, req);
        if (placed.payment() == null) return placed;
        return placed.withPayment(payments.openSession(placed.checkoutRef(), userId));
    }
}
