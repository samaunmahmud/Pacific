package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.CartItem;
import com.pacific.marketplace.domain.DeliveryOption;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderEventType;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.PaymentMethod;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.ShippingAddress;
import com.pacific.marketplace.notify.NotificationService;
import com.pacific.marketplace.repo.CartItemRepository;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutRequest;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutResponse;
import com.pacific.marketplace.web.dto.OrderDtos.OrderDto;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.PaymentDtos.PaymentDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final long HOUSE = 0L; // grouping key for Pacific's own products

    private final OrderRepository orders;
    private final CartItemRepository cart;
    private final ProductRepository products;
    private final BuyBox buyBox;
    private final Delivery delivery;
    private final CartService carts;
    private final Promotions promotions;
    private final UserRepository users;
    private final ShopPricing pricing;
    private final SettingsService settings;
    private final LedgerService ledger;
    private final OrderCancellation cancellation;
    private final PaymentService payments;
    private final NotificationService notifications;

    public OrderService(OrderRepository orders, CartItemRepository cart, ProductRepository products,
                        UserRepository users, ShopPricing pricing, SettingsService settings, LedgerService ledger,
                        OrderCancellation cancellation, PaymentService payments,
                        NotificationService notifications, BuyBox buyBox, Delivery delivery,
                        CartService carts, Promotions promotions) {
        this.buyBox = buyBox;
        this.delivery = delivery;
        this.carts = carts;
        this.promotions = promotions;
        this.orders = orders;
        this.cart = cart;
        this.products = products;
        this.users = users;
        this.pricing = pricing;
        this.settings = settings;
        this.ledger = ledger;
        this.cancellation = cancellation;
        this.payments = payments;
        this.notifications = notifications;
    }

    /**
     * Turns the customer's cart into orders, one per seller (Pacific's own products form their own order).
     * Stock is taken with atomic conditional updates, so two customers can never both buy the last unit; if
     * anything is short, the whole checkout rolls back.
     */
    @Transactional
    public CheckoutResponse checkout(Long userId, CheckoutRequest req) {
        boolean card = req.paymentMethod() == PaymentMethod.CARD;
        if (card) payments.requireCardAvailable(); // before any stock is reserved

        List<CartItem> items = cart.findByUserIdAndSavedForLaterFalseOrderById(userId);
        if (items.isEmpty()) throw ApiException.badRequest("Your cart is empty.");

        // Fixed order (by product id) keeps concurrent checkouts from deadlocking on each other's rows.
        List<CartItem> sorted = items.stream().sorted(Comparator.comparing(i -> i.getProduct().getId())).toList();
        for (CartItem item : sorted) {
            Product p = item.getProduct();
            if (p.getSeller() != null && p.getSeller().getUser().getId().equals(userId)) {
                throw ApiException.conflict("You can't buy your own product (" + p.getName() + "). Please remove it.");
            }
            if (item.getQuantity() > pricing.maxQuantityPerItem()) {
                throw ApiException.conflict("You can buy at most " + pricing.maxQuantityPerItem()
                        + " of each item per order.");
            }
            if (products.decrementStock(p.getId(), item.getQuantity()) == 0) {
                throw ApiException.conflict(p.getName() + " no longer has enough stock. Please update your cart.");
            }
        }
        // a listing that just sold out hands the buy box to the next seller
        buyBox.refreshFor(items.stream().map(i -> i.getProduct().getId()).toList());

        // Deals, clipped coupons and a promo code, priced exactly as the cart showed them; claimed below.
        CartService.Priced priced = carts.price(items, userId, req.promoCode());
        if (priced.promoError() != null) throw ApiException.badRequest(priced.promoError());

        Map<Long, List<CartItem>> bySeller = new LinkedHashMap<>();
        for (CartItem item : items) {
            SellerProfile seller = item.getProduct().getSeller();
            bySeller.computeIfAbsent(seller == null ? HOUSE : seller.getId(), k -> new ArrayList<>()).add(item);
        }

        String ref = UUID.randomUUID().toString();
        ShippingAddress address = new ShippingAddress(req.name().strip(), req.line1().strip(),
                Text.clean(req.line2()), req.city().strip(), req.postcode().strip(), req.country().strip());
        List<OrderDto> created = new ArrayList<>();
        List<Order> placed = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (List<CartItem> group : bySeller.values()) {
            SellerProfile seller = group.get(0).getProduct().getSeller();
            // the commission rate is fixed now, so later rate changes don't alter this order
            Order order = new Order(users.getReferenceById(userId), address, seller, ref,
                    seller == null ? null : settings.effectiveCommission(seller));
            if (card) {
                // Stock is reserved, but it is not a purchase (or visible to the seller) until it is paid.
                order.setStatus(OrderStatus.AWAITING_PAYMENT);
                order.setPaymentMethod(PaymentMethod.CARD.name());
                order.addEvent(OrderEventType.AWAITING_PAYMENT, "Waiting for the card payment");
            } else {
                order.addEvent(OrderEventType.PLACED, "Pay on delivery");
            }
            BigDecimal subtotal = BigDecimal.ZERO;
            for (CartItem item : group) {
                Product p = item.getProduct();
                OrderItem.Pricing price = priced.prices().get(item);
                order.addItem(p, item.getQuantity(), price);
                subtotal = subtotal.add(price.unitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            subtotal = subtotal.setScale(2);
            DeliveryOption option = req.delivery() == null ? null : req.delivery().get(CartService.shipmentKey(seller));
            if (option == null) option = DeliveryOption.STANDARD;
            Delivery.Window window = delivery.window(option, seller);
            order.setDelivery(option, window.from(), window.to());
            order.setTotals(subtotal, delivery.fee(option, subtotal, seller));
            orders.save(order);
            promotions.claim(order, userId); // deal units, coupon and code uses; throws (undoing it all) if one ran out
            placed.add(order);
            created.add(OrderDto.from(order));
            grandTotal = grandTotal.add(order.getTotal());
        }
        cart.deleteCheckedOutForUser(userId);
        if (!card) notifications.ordersPlaced(placed); // a card checkout is announced once it has been paid
        // The payment is recorded in the same transaction as the orders; the provider is contacted afterwards.
        PaymentDto payment = card
                ? PaymentDto.from(payments.createPending(users.getReferenceById(userId), ref, grandTotal)) : null;
        return new CheckoutResponse(ref, created, grandTotal, payment);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> myOrders(Long userId) {
        return orders.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(OrderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto myOrder(Long userId, Long orderId) {
        return OrderDto.from(ownOrder(userId, orderId));
    }

    /** Customers can cancel only while the order is still PLACED (before the seller has started preparing it). */
    @Transactional
    public OrderDto cancelMine(Long userId, Long orderId) {
        Order order = orders.lockById(orderId).filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order not found."));
        if (order.getStatus() != OrderStatus.PLACED) {
            throw ApiException.conflict("This order is already being prepared and can no longer be cancelled.");
        }
        cancel(order, "you");
        return OrderDto.from(order);
    }

    // ---------- seller ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderDto> sellerList(Long sellerId, OrderStatus status, int page, int size) {
        if (status == OrderStatus.AWAITING_PAYMENT) { // unpaid card orders stay invisible to sellers
            return PageResponse.of(Page.empty(pageable(page, size)), OrderDto::from);
        }
        var result = status == null ? orders.findBySellerIdAndStatusNot(sellerId, OrderStatus.AWAITING_PAYMENT, pageable(page, size))
                : orders.findBySellerIdAndStatus(sellerId, status, pageable(page, size));
        return PageResponse.of(result, OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDto sellerGet(Long sellerId, Long orderId) {
        return orders.findWithItemsById(orderId).filter(o -> visibleToSeller(o, sellerId)).map(OrderDto::from)
                .orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    @Transactional
    public OrderDto sellerSetStatus(Long sellerId, Long orderId, OrderStatus next, String carrier, String trackingNumber) {
        Order order = orders.lockById(orderId).filter(o -> visibleToSeller(o, sellerId))
                .orElseThrow(() -> ApiException.notFound("Order not found."));
        transition(order, next, "the seller", carrier, trackingNumber);
        return OrderDto.from(order);
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderDto> adminList(OrderStatus status, int page, int size) {
        var result = status == null ? orders.findAll(pageable(page, size))
                : orders.findByStatus(status, pageable(page, size));
        return PageResponse.of(result, OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDto adminGet(Long orderId) {
        return orders.findWithItemsById(orderId).map(OrderDto::from)
                .orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    @Transactional
    public OrderDto adminSetStatus(Long orderId, OrderStatus next, String carrier, String trackingNumber) {
        Order order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order not found."));
        transition(order, next, "Pacific", carrier, trackingNumber);
        return OrderDto.from(order);
    }

    // ---------- helpers ----------

    /**
     * The single place where order status changes: validates the move, records it on the order's timeline, restocks on
     * cancel, books earnings on delivery, and tells the customer. by says who did it, for the timeline and emails.
     */
    private void transition(Order order, OrderStatus next, String by, String carrier, String trackingNumber) {
        if (!order.getStatus().allowedNext().contains(next)) {
            throw ApiException.conflict("An order that is " + order.getStatus() + " can't be moved to " + next + ".");
        }
        switch (next) {
            case CANCELLED -> cancel(order, by);
            case PROCESSING -> {
                order.setStatus(next);
                order.addEvent(OrderEventType.PROCESSING, null);
            }
            case SHIPPED -> {
                String c = Text.clean(carrier);
                String n = Text.clean(trackingNumber);
                order.setStatus(next);
                order.markShipped(c, n);
                order.addEvent(OrderEventType.SHIPPED, n == null ? null : (c == null ? "" : c + " ") + n);
                notifications.orderShipped(order);
            }
            case DELIVERED -> {
                order.setStatus(next);
                order.markDelivered(pricing.returnWindow());
                order.addEvent(OrderEventType.DELIVERED, null);
                ledger.recordDelivered(order);
                notifications.orderDelivered(order);
            }
            default -> order.setStatus(next);
        }
    }

    private void cancel(Order order, String by) {
        cancellation.cancel(order);
        BigDecimal refunded = payments.refundOrder(order); // a paid card order gets its money back
        boolean money = refunded.signum() > 0;
        order.addEvent(OrderEventType.CANCELLED, "Cancelled by " + by + (money ? ", refunded to the card" : ""));
        notifications.orderCancelled(order, by, refunded);
    }

    private static boolean belongsTo(Order order, Long sellerId) {
        return order.getSeller() != null && order.getSeller().getId().equals(sellerId);
    }

    /** A seller's own order, once it is real: card orders that haven't been paid yet don't exist for the seller. */
    private static boolean visibleToSeller(Order order, Long sellerId) {
        return belongsTo(order, sellerId) && order.getStatus() != OrderStatus.AWAITING_PAYMENT;
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private Order ownOrder(Long userId, Long orderId) {
        return orders.findWithItemsById(orderId).filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order not found."));
    }
}
