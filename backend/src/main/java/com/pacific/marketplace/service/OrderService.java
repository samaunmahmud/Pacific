package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.CartItem;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.ShippingAddress;
import com.pacific.marketplace.repo.CartItemRepository;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutRequest;
import com.pacific.marketplace.web.dto.OrderDtos.CheckoutResponse;
import com.pacific.marketplace.web.dto.OrderDtos.OrderDto;
import com.pacific.marketplace.web.dto.PageResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final UserRepository users;
    private final ShopPricing pricing;
    private final SettingsService settings;
    private final LedgerService ledger;

    public OrderService(OrderRepository orders, CartItemRepository cart, ProductRepository products,
                        UserRepository users, ShopPricing pricing, SettingsService settings, LedgerService ledger) {
        this.orders = orders;
        this.cart = cart;
        this.products = products;
        this.users = users;
        this.pricing = pricing;
        this.settings = settings;
        this.ledger = ledger;
    }

    /**
     * Turns the customer's cart into orders, one per seller (Pacific's own products form their own order).
     * Stock is taken with atomic conditional updates, so two customers can never both buy the last unit; if
     * anything is short, the whole checkout rolls back.
     */
    @Transactional
    public CheckoutResponse checkout(Long userId, CheckoutRequest req) {
        List<CartItem> items = cart.findByUserIdOrderById(userId);
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

        Map<Long, List<CartItem>> bySeller = new LinkedHashMap<>();
        for (CartItem item : items) {
            SellerProfile seller = item.getProduct().getSeller();
            bySeller.computeIfAbsent(seller == null ? HOUSE : seller.getId(), k -> new ArrayList<>()).add(item);
        }

        String ref = UUID.randomUUID().toString();
        ShippingAddress address = new ShippingAddress(req.name().strip(), req.line1().strip(),
                Text.clean(req.line2()), req.city().strip(), req.postcode().strip(), req.country().strip());
        List<OrderDto> created = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (List<CartItem> group : bySeller.values()) {
            SellerProfile seller = group.get(0).getProduct().getSeller();
            // the commission rate is fixed now, so later rate changes don't alter this order
            Order order = new Order(users.getReferenceById(userId), address, seller, ref,
                    seller == null ? null : settings.effectiveCommission(seller));
            BigDecimal subtotal = BigDecimal.ZERO;
            for (CartItem item : group) {
                Product p = item.getProduct();
                order.addItem(p, item.getQuantity());
                subtotal = subtotal.add(p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            subtotal = subtotal.setScale(2);
            order.setTotals(subtotal, pricing.shippingFor(subtotal));
            orders.save(order);
            created.add(OrderDto.from(order));
            grandTotal = grandTotal.add(order.getTotal());
        }
        cart.deleteAllForUser(userId);
        return new CheckoutResponse(ref, created, grandTotal);
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
        cancel(order);
        return OrderDto.from(order);
    }

    // ---------- seller ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderDto> sellerList(Long sellerId, OrderStatus status, int page, int size) {
        var result = status == null ? orders.findBySellerId(sellerId, pageable(page, size))
                : orders.findBySellerIdAndStatus(sellerId, status, pageable(page, size));
        return PageResponse.of(result, OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDto sellerGet(Long sellerId, Long orderId) {
        return orders.findWithItemsById(orderId).filter(o -> belongsTo(o, sellerId)).map(OrderDto::from)
                .orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    @Transactional
    public OrderDto sellerSetStatus(Long sellerId, Long orderId, OrderStatus next) {
        Order order = orders.lockById(orderId).filter(o -> belongsTo(o, sellerId))
                .orElseThrow(() -> ApiException.notFound("Order not found."));
        transition(order, next);
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
    public OrderDto adminSetStatus(Long orderId, OrderStatus next) {
        Order order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order not found."));
        transition(order, next);
        return OrderDto.from(order);
    }

    // ---------- helpers ----------

    /** The single place where order status changes: validates the move, restocks on cancel, books earnings on delivery. */
    private void transition(Order order, OrderStatus next) {
        if (!order.getStatus().allowedNext().contains(next)) {
            throw ApiException.conflict("An order that is " + order.getStatus() + " can't be moved to " + next + ".");
        }
        if (next == OrderStatus.CANCELLED) {
            cancel(order);
        } else {
            order.setStatus(next);
            if (next == OrderStatus.DELIVERED) ledger.recordDelivered(order);
        }
    }

    private void cancel(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        for (OrderItem item : order.getItems()) {
            products.incrementStock(item.getProduct().getId(), item.getQuantity());
        }
    }

    private static boolean belongsTo(Order order, Long sellerId) {
        return order.getSeller() != null && order.getSeller().getId().equals(sellerId);
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
