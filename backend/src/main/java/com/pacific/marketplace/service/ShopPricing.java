package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ShopPricing {

    private final AppProperties.Shop shop;

    public ShopPricing(AppProperties props) {
        this.shop = props.shop();
    }

    /** Flat-rate shipping, free once the subtotal reaches the threshold. Nothing to ship for an empty basket. */
    public BigDecimal shippingFor(BigDecimal subtotal) {
        if (subtotal.signum() == 0) return BigDecimal.ZERO.setScale(2);
        if (subtotal.compareTo(shop.freeShippingThreshold()) >= 0) return BigDecimal.ZERO.setScale(2);
        return shop.shippingFlatRate().setScale(2);
    }

    public BigDecimal freeShippingThreshold() {
        return shop.freeShippingThreshold();
    }

    /** How many days after delivery a customer may ask to return items. */
    public java.time.Duration returnWindow() {
        return java.time.Duration.ofDays(shop.returnWindowDays() > 0 ? shop.returnWindowDays() : 30);
    }

    public int maxQuantityPerItem() {
        return shop.maxQuantityPerItem();
    }
}
