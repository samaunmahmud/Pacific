package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.repo.ProductRepository;
import org.springframework.stereotype.Component;

/**
 * Cancels an order and puts its stock back. Shared by order handling and payment handling so that neither has to
 * depend on the other. The caller must already hold the order's lock, so stock is restored at most once.
 */
@Component
public class OrderCancellation {

    private final ProductRepository products;
    private final BuyBox buyBox;
    private final Promotions promotions;

    public OrderCancellation(ProductRepository products, BuyBox buyBox, Promotions promotions) {
        this.buyBox = buyBox;
        this.promotions = promotions;
        this.products = products;
    }

    public void cancel(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        for (OrderItem item : order.getItems()) {
            products.incrementStock(item.getProduct().getId(), item.getQuantity());
        }
        promotions.release(order); // deal units, coupon and promo code uses go back
        buyBox.refreshFor(order.getItems().stream().map(i -> i.getProduct().getId()).toList());
    }
}
