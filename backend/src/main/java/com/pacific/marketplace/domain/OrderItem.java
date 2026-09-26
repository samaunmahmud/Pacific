package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Name and price are snapshotted so order history stays correct if the product changes later. */
    @Column(name = "product_name", nullable = false, length = 160)
    private String productName;

    @Column(length = 100)
    private String variation;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    /** The price before promotions (null when none applied, and on orders from before promotions). */
    @Column(name = "list_unit_price", precision = 10, scale = 2)
    private BigDecimal listUnitPrice;

    /** The promotions this line used, so a cancelled order can give them back. */
    @Column(name = "deal_id")
    private Long dealId;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "promo_id")
    private Long promoId;

    /** What the shopper saw, e.g. "Lightning Deal" or "Coupon 10%" or "Code SPRING15". */
    @Column(length = 60)
    private String promotion;

    protected OrderItem() {
    }

    public OrderItem(Order order, Product product, int quantity) {
        this(order, product, quantity, Pricing.regular(product));
    }

    public OrderItem(Order order, Product product, int quantity, Pricing price) {
        this.order = order;
        this.product = product;
        this.productName = product.getName();
        this.variation = product.getVariation();
        this.unitPrice = price.unitPrice();
        this.quantity = quantity;
        if (price.unitPrice().compareTo(product.getPrice()) != 0) this.listUnitPrice = product.getPrice();
        this.dealId = price.dealId();
        this.couponId = price.couponId();
        this.promoId = price.promoId();
        this.promotion = price.label();
    }

    /** A line's price after promotions, and which ones it used. */
    public record Pricing(BigDecimal unitPrice, Long dealId, Long couponId, Long promoId, String label) {
        public static Pricing regular(Product p) {
            return new Pricing(p.getPrice(), null, null, null, null);
        }
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public Product getProduct() { return product; }
    public String getProductName() { return productName; }
    public String getVariation() { return variation; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getListUnitPrice() { return listUnitPrice; }
    public Long getDealId() { return dealId; }
    public Long getCouponId() { return couponId; }
    public Long getPromoId() { return promoId; }
    public String getPromotion() { return promotion; }
}
