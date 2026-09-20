package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.OrderEventType;
import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.Payment;
import com.pacific.marketplace.domain.PaymentMethod;
import com.pacific.marketplace.domain.PaymentProviderType;
import com.pacific.marketplace.domain.PaymentStatus;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.notify.NotificationService;
import com.pacific.marketplace.payment.PaymentGateway;
import com.pacific.marketplace.payment.PaymentGateway.CheckoutSpec;
import com.pacific.marketplace.payment.PaymentGateway.GatewayException;
import com.pacific.marketplace.payment.PaymentGateway.GatewaySession;
import com.pacific.marketplace.payment.PaymentGateway.GatewayStatus;
import com.pacific.marketplace.payment.PaymentGateway.Line;
import com.pacific.marketplace.payment.PaymentGateway.State;
import com.pacific.marketplace.payment.PaymentGateways;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.PaymentRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.PaymentDtos.PaymentConfigDto;
import com.pacific.marketplace.web.dto.PaymentDtos.PaymentDto;
import com.pacific.marketplace.web.dto.PaymentDtos.SimulatedOutcome;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Card payments. One payment covers every order of a checkout, and it moves through
 * PENDING -> PAID, EXPIRED or CANCELLED, exactly once.
 *
 * <p>Talking to the payment provider is slow and can fail, so it never happens inside a database transaction that
 * holds locks: the methods here look at the payment, call the provider, and then apply the result in a short
 * transaction that re-checks the payment under its row lock. That re-check is what makes paying, cancelling,
 * expiring and webhook retries safe to race against each other.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderCancellation cancellation;
    private final CartService carts;
    private final NotificationService notifications;
    private final PaymentGateways gateways;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderCancellation cancellation,
                          CartService carts, NotificationService notifications, PaymentGateways gateways,
                          AppProperties props, PlatformTransactionManager txManager) {
        this.payments = payments;
        this.orders = orders;
        this.cancellation = cancellation;
        this.carts = carts;
        this.notifications = notifications;
        this.gateways = gateways;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    /** The parts of a payment needed to decide what to do next, safe to use outside a transaction. */
    private record View(PaymentStatus status, PaymentProviderType provider, String sessionId) {
    }

    // ---------- starting a payment ----------

    public PaymentConfigDto config() {
        return new PaymentConfigDto(gateways.active().isPresent(), gateways.simulatorActive());
    }

    /** Checked before any stock is reserved, so a card checkout fails early when card payments are switched off. */
    public void requireCardAvailable() {
        if (gateways.active().isEmpty()) {
            throw ApiException.badRequest("Card payments aren't available right now. Please pay on delivery instead.");
        }
    }

    /** Called inside the checkout transaction: records the payment before the provider is contacted. */
    public Payment createPending(User user, String checkoutRef, BigDecimal amount) {
        PaymentGateway gateway = gateways.active().orElseThrow(
                () -> ApiException.badRequest("Card payments aren't available right now."));
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(props.payments().pendingExpiryMinutes()));
        return payments.save(new Payment(checkoutRef, user, gateway.type(), null, null, amount,
                props.shop().currency(), expiresAt));
    }

    /**
     * Creates the provider's hosted checkout page for a payment made by createPending. Safe to call again: a payment
     * that already has a page just returns it. If the provider can't be reached the checkout is cancelled and the
     * reserved stock released.
     */
    public PaymentDto openSession(String checkoutRef, Long userId) {
        record Prepared(PaymentGateway gateway, CheckoutSpec spec) {
        }
        Prepared prepared = tx.execute(s -> {
            Payment p = payments.findByCheckoutRef(checkoutRef).filter(x -> x.getUser().getId().equals(userId))
                    .orElseThrow(() -> ApiException.notFound("Payment not found."));
            if (p.getStatus() != PaymentStatus.PENDING) throw ApiException.conflict("This payment is no longer open.");
            if (p.getProviderSessionId() != null) return null; // already opened
            PaymentGateway gateway = gateways.forType(p.getProvider()).orElseThrow(
                    () -> ApiException.badRequest("Card payments aren't available right now."));
            return new Prepared(gateway, specFor(p));
        });
        if (prepared == null) return dto(checkoutRef);

        GatewaySession session;
        try {
            session = prepared.gateway().create(prepared.spec());
        } catch (GatewayException e) {
            log.warn("Could not start card payment {}: {}", checkoutRef, e.getMessage());
            finish(checkoutRef, PaymentStatus.CANCELLED);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "We couldn't start the card payment. Nothing was charged; please try again.");
        }
        return tx.execute(s -> {
            Payment p = payments.lockByCheckoutRef(checkoutRef).orElseThrow(
                    () -> ApiException.notFound("Payment not found."));
            if (p.getStatus() != PaymentStatus.PENDING) {
                closeQuietly(prepared.gateway(), session.sessionId());
                throw ApiException.conflict("This payment is no longer open.");
            }
            p.attachSession(session.sessionId(), session.url());
            return PaymentDto.from(p);
        });
    }

    /** The provider's line items: every product, plus one shipping line per order that has shipping. */
    private CheckoutSpec specFor(Payment p) {
        List<Order> checkoutOrders = orders.findByCheckoutRef(p.getCheckoutRef());
        List<Line> lines = new ArrayList<>();
        long total = 0;
        for (Order order : checkoutOrders) {
            for (OrderItem item : order.getItems()) {
                lines.add(new Line(item.getProductName(), minor(item.getUnitPrice()), item.getQuantity()));
                total += minor(item.getUnitPrice()) * item.getQuantity();
            }
            if (order.getShipping().signum() > 0) {
                String store = order.getSeller() == null ? ProductDto.HOUSE_STORE : order.getSeller().getStoreName();
                lines.add(new Line(checkoutOrders.size() > 1 ? "Shipping (" + store + ")" : "Shipping",
                        minor(order.getShipping()), 1));
                total += minor(order.getShipping());
            }
        }
        if (total != minor(p.getAmount())) {
            // The customer must be charged exactly what the orders add up to; refuse rather than charge something else.
            throw new IllegalStateException("Payment " + p.getCheckoutRef() + " does not match its orders.");
        }
        String base = props.publicUrl().replaceAll("/+$", "");
        String returnUrl = base + "/pay/return?ref=" + p.getCheckoutRef();
        return new CheckoutSpec(p.getCheckoutRef(), p.getUser().getEmail(), p.getCurrency(), lines, p.getExpiresAt(),
                returnUrl, returnUrl + "&cancelled=1");
    }

    // ---------- reading ----------

    /**
     * The customer's own payment. If it is still pending we first ask the provider what happened, so the return page
     * works even when the webhook is late or can't reach us (for example on a developer's laptop).
     */
    public PaymentDto get(String checkoutRef, Long userId) {
        View v = require(checkoutRef, userId);
        if (v.status() == PaymentStatus.PENDING && v.sessionId() != null) {
            gateways.forType(v.provider()).ifPresent(gateway -> {
                try {
                    GatewayStatus status = gateway.status(v.sessionId());
                    if (status.state() == State.PAID) markPaid(checkoutRef, status.paymentRef());
                    else if (status.state() == State.EXPIRED) finish(checkoutRef, PaymentStatus.EXPIRED);
                } catch (GatewayException e) {
                    log.warn("Could not check payment {} with the provider: {}", checkoutRef, e.getMessage());
                }
            });
        }
        return dto(checkoutRef);
    }

    // ---------- outcomes ----------

    public void markPaid(String checkoutRef, String providerPaymentRef) {
        markPaid(checkoutRef, providerPaymentRef, null, null);
    }

    /**
     * The provider says this checkout was paid. Idempotent: repeated webhooks change nothing. If the payment had
     * already been expired or cancelled (the customer paid at the last moment, after their stock was released) the
     * money is refunded instead, since the goods are no longer reserved. paidMinor and paidCurrency, when given,
     * must match what we asked for.
     */
    public void markPaid(String checkoutRef, String providerPaymentRef, Long paidMinor, String paidCurrency) {
        tx.executeWithoutResult(s -> {
            Payment p = payments.lockByCheckoutRef(checkoutRef).orElse(null);
            if (p == null) {
                log.warn("Ignoring a payment for unknown checkout {}", checkoutRef);
                return;
            }
            if (paidMinor != null && (paidMinor != minor(p.getAmount())
                    || paidCurrency == null || !p.getCurrency().equalsIgnoreCase(paidCurrency))) {
                log.error("Ignoring payment for checkout {}: provider reports {} {} but we asked for {} {}",
                        checkoutRef, paidMinor, paidCurrency, minor(p.getAmount()), p.getCurrency());
                return;
            }
            switch (p.getStatus()) {
                case PAID -> { /* already recorded */ }
                case PENDING -> {
                    p.markPaid(providerPaymentRef);
                    List<Order> nowPlaced = new ArrayList<>();
                    for (Order order : orders.findByCheckoutRef(checkoutRef)) {
                        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) continue;
                        order.setStatus(OrderStatus.PLACED);
                        order.addEvent(OrderEventType.PAYMENT_RECEIVED, "Paid by card");
                        order.addEvent(OrderEventType.PLACED, null);
                        nowPlaced.add(order);
                    }
                    notifications.ordersPlaced(nowPlaced);
                }
                case EXPIRED, CANCELLED -> refundLatePayment(p, providerPaymentRef);
            }
        });
    }

    private void refundLatePayment(Payment p, String providerPaymentRef) {
        if (providerPaymentRef == null) {
            throw new IllegalStateException("Late payment for " + p.getCheckoutRef() + " has no provider reference.");
        }
        PaymentGateway gateway = gateways.forType(p.getProvider()).orElseThrow(
                () -> new IllegalStateException("No provider configured to refund " + p.getCheckoutRef()));
        p.markPaid(providerPaymentRef);
        // If the refund fails this throws, the transaction rolls back and the provider retries its webhook.
        gateway.refund(providerPaymentRef, p.getAmount(), p.getCurrency(), "pacific-refund-late-" + p.getCheckoutRef());
        p.addRefund(p.getAmount());
        log.warn("Payment {} arrived after the checkout was released; refunded in full.", p.getCheckoutRef());
    }

    /** The customer gives up on paying. Their unpaid orders are cancelled and the stock goes back on sale. */
    public PaymentDto cancel(String checkoutRef, Long userId) {
        View v = require(checkoutRef, userId);
        if (v.status() == PaymentStatus.PENDING) {
            if (closeSession(checkoutRef, v) || finish(checkoutRef, PaymentStatus.CANCELLED) == PaymentStatus.PAID) {
                throw ApiException.conflict("Your payment had just gone through, so your order has been placed.");
            }
        } else if (v.status() == PaymentStatus.PAID) {
            throw ApiException.conflict("This payment has already been made. Cancel the order to get a refund.");
        }
        return dto(checkoutRef);
    }

    /** A payment nobody completed in time. Used by the expiry job and when the provider reports the session expired. */
    public void expire(String checkoutRef) {
        View v = view(checkoutRef, null);
        if (v == null) {
            log.warn("Ignoring expiry of unknown checkout {}", checkoutRef);
            return;
        }
        if (v.status() != PaymentStatus.PENDING) return;
        if (closeSession(checkoutRef, v)) return; // it was paid after all
        finish(checkoutRef, PaymentStatus.EXPIRED);
    }

    /** Expires every payment that ran out of time. One failure doesn't stop the others; it is retried next run. */
    public int expireOverdue() {
        int expired = 0;
        for (String ref : payments.findRefsByStatusAndExpiresBefore(PaymentStatus.PENDING, Instant.now())) {
            try {
                expire(ref);
                expired++;
            } catch (RuntimeException e) {
                log.warn("Could not expire payment {}: {}", ref, e.getMessage());
            }
        }
        return expired;
    }

    /** Test-mode only: plays the part of the customer on the simulator's "hosted page". */
    public PaymentDto simulate(String checkoutRef, Long userId, SimulatedOutcome outcome) {
        View v = require(checkoutRef, userId);
        if (v.provider() != PaymentProviderType.SIMULATOR || gateways.forType(PaymentProviderType.SIMULATOR).isEmpty()) {
            throw ApiException.notFound("Not found.");
        }
        if (outcome == SimulatedOutcome.PAID) {
            markPaid(checkoutRef, "sim_pi_" + checkoutRef);
            return dto(checkoutRef);
        }
        return cancel(checkoutRef, userId);
    }

    // ---------- refunds for cancelled orders ----------

    /**
     * Gives the customer their money back when a paid card order is cancelled (by them, the seller or an admin).
     * Runs inside the cancelling transaction: if the provider refuses, the cancellation is rolled back too.
     * Returns the amount refunded (zero when nothing had been paid by card).
     */
    public BigDecimal refundOrder(Order order) {
        if (!PaymentMethod.CARD.name().equals(order.getPaymentMethod()) || order.getCheckoutRef() == null) return BigDecimal.ZERO;
        Payment p = payments.lockByCheckoutRef(order.getCheckoutRef()).orElse(null);
        if (p == null || p.getStatus() != PaymentStatus.PAID) return BigDecimal.ZERO;
        BigDecimal amount = order.getTotal();
        if (p.refundable().compareTo(amount) < 0) {
            throw ApiException.conflict("This order has already been refunded.");
        }
        PaymentGateway gateway = gateways.forType(p.getProvider()).orElseThrow(
                () -> ApiException.conflict("The payment provider for this order isn't available, so it can't be refunded now."));
        gateway.refund(p.getProviderPaymentRef(), amount, p.getCurrency(), "pacific-refund-order-" + order.getId());
        p.addRefund(amount);
        return amount;
    }

    // ---------- helpers ----------

    /**
     * Stops the customer paying on the provider's page. Returns true if it turns out they already had paid (which is
     * then recorded). If the provider can't say, we refuse to cancel: cancelling while they might be paying would
     * take their money for an order we released.
     */
    private boolean closeSession(String checkoutRef, View v) {
        if (v.sessionId() == null) return false;
        PaymentGateway gateway = gateways.forType(v.provider()).orElse(null);
        if (gateway == null) return false;
        try {
            GatewayStatus status = gateway.status(v.sessionId());
            if (status.state() == State.PAID) {
                markPaid(checkoutRef, status.paymentRef());
                return true;
            }
            if (status.state() == State.OPEN) gateway.expire(v.sessionId());
            return false;
        } catch (GatewayException e) {
            log.warn("Could not close the provider session for {}: {}", checkoutRef, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "We couldn't close the card payment just now. Please try again in a moment.");
        }
    }

    private static void closeQuietly(PaymentGateway gateway, String sessionId) {
        try {
            gateway.expire(sessionId);
        } catch (GatewayException e) {
            log.warn("Could not close provider session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Ends a still-pending payment (unless it was paid meanwhile) and cancels its orders. Returns the final status.
     * The customer's items go back in their cart, so a cancelled, timed-out or failed-to-start card checkout can
     * simply be tried again.
     */
    private PaymentStatus finish(String checkoutRef, PaymentStatus target) {
        record Released(PaymentStatus status, Long userId, Map<Long, Integer> items) {
        }
        Released released = tx.execute(s -> {
            Payment p = payments.lockByCheckoutRef(checkoutRef).orElseThrow(
                    () -> ApiException.notFound("Payment not found."));
            if (p.getStatus() != PaymentStatus.PENDING) return new Released(p.getStatus(), null, Map.of());
            p.close(target);
            Map<Long, Integer> items = new LinkedHashMap<>();
            for (Order order : orders.findByCheckoutRef(checkoutRef)) {
                if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) continue;
                cancellation.cancel(order);
                order.addEvent(OrderEventType.CANCELLED, target == PaymentStatus.EXPIRED
                        ? "Payment not completed in time" : "Payment cancelled");
                for (OrderItem item : order.getItems()) items.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            }
            return new Released(target, p.getUser().getId(), items);
        });
        restoreCart(released.userId(), released.items());
        return released.status();
    }

    /**
     * Separate from the cancellation and never fatal: a cart that can't be refilled (say the customer added the same
     * item at the same moment) must not undo releasing the stock.
     */
    private void restoreCart(Long userId, Map<Long, Integer> items) {
        if (userId == null || items.isEmpty()) return;
        try {
            tx.executeWithoutResult(s -> carts.restore(userId, items));
        } catch (RuntimeException e) {
            log.warn("Could not put a released checkout's items back in the cart of user {}: {}", userId, e.getMessage());
        }
    }

    private View view(String checkoutRef, Long userId) {
        return tx.execute(s -> payments.findByCheckoutRef(checkoutRef)
                .filter(p -> userId == null || p.getUser().getId().equals(userId))
                .map(p -> new View(p.getStatus(), p.getProvider(), p.getProviderSessionId()))
                .orElse(null));
    }

    private View require(String checkoutRef, Long userId) {
        View v = view(checkoutRef, userId);
        if (v == null) throw ApiException.notFound("Payment not found.");
        return v;
    }

    private PaymentDto dto(String checkoutRef) {
        return tx.execute(s -> payments.findByCheckoutRef(checkoutRef).map(PaymentDto::from)
                .orElseThrow(() -> ApiException.notFound("Payment not found.")));
    }

    /** Pounds and pence as the provider's smallest unit (pence). */
    public static long minor(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }
}
