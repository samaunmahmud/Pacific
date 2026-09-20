package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Carriers;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderEvent;
import com.pacific.marketplace.domain.OrderEventType;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.PaymentMethod;
import com.pacific.marketplace.domain.ShippingAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(
            @NotBlank(message = "Please enter the recipient's name.") @Size(max = 120) String name,
            @NotBlank(message = "Please enter the first line of the address.") @Size(max = 160) String line1,
            @Size(max = 160) String line2,
            @NotBlank(message = "Please enter the town or city.") @Size(max = 80) String city,
            @NotBlank(message = "Please enter the postcode.") @Size(max = 20) String postcode,
            @NotBlank(message = "Please enter the country.") @Size(max = 80) String country,
            /** Pay on delivery when omitted. */
            PaymentMethod paymentMethod) {
    }

    /** carrier and trackingNumber are only used when the order is being marked as shipped, and are optional. */
    public record StatusRequest(@NotNull(message = "Status is required.") OrderStatus status,
                                @Size(max = 60, message = "Carrier can be at most 60 characters.") String carrier,
                                @Size(max = 80, message = "Tracking number can be at most 80 characters.") String trackingNumber) {
    }

    public record EventDto(OrderEventType type, String note, Instant at) {
        static EventDto from(OrderEvent e) {
            return new EventDto(e.getType(), e.getNote(), e.getCreatedAt());
        }
    }

    public record AddressDto(String name, String line1, String line2, String city, String postcode, String country) {
        static AddressDto from(ShippingAddress a) {
            return new AddressDto(a.getName(), a.getLine1(), a.getLine2(), a.getCity(), a.getPostcode(),
                    a.getCountry());
        }
    }

    /** imageUrl and categoryName come from the product as it is now (name and price are the order's own snapshot). */
    public record OrderItemDto(Long id, Long productId, String productName, BigDecimal unitPrice, int quantity,
                               BigDecimal lineTotal, String imageUrl, String categoryName, int returnableQuantity) {
        static OrderItemDto from(OrderItem i) {
            var product = i.getProduct();
            return new OrderItemDto(i.getId(), product.getId(), i.getProductName(), i.getUnitPrice(), i.getQuantity(),
                    i.lineTotal(), product.getImageUrl(),
                    product.getCategory() == null ? null : product.getCategory().getName(),
                    i.getOrder().canReturn() ? i.getOrder().returnableUnits(i) : 0);
        }
    }

    public record OrderDto(Long id, OrderStatus status, BigDecimal subtotal, BigDecimal shipping, BigDecimal total,
                           String paymentMethod, AddressDto address, List<OrderItemDto> items, int itemCount,
                           String customerName, String sellerName, String sellerSlug, Long sellerId,
                           String checkoutRef, Set<OrderStatus> nextStatuses, boolean cancellableByCustomer,
                           Instant createdAt, String trackingCarrier, String trackingNumber, String trackingUrl,
                           Instant shippedAt, Instant deliveredAt, List<EventDto> timeline, boolean canReturn,
                           Instant returnDeadline, List<ReturnDtos.ReturnDto> returns) {
        public static OrderDto from(Order o) {
            List<OrderItemDto> items = o.getItems().stream().map(OrderItemDto::from).toList();
            var seller = o.getSeller();
            return new OrderDto(o.getId(), o.getStatus(), o.getSubtotal(), o.getShipping(), o.getTotal(),
                    o.getPaymentMethod(), AddressDto.from(o.getAddress()), items,
                    items.stream().mapToInt(OrderItemDto::quantity).sum(), o.getUser().getName(),
                    seller == null ? ProductDtos.ProductDto.HOUSE_STORE : seller.getStoreName(),
                    seller == null ? null : seller.getSlug(), seller == null ? null : seller.getId(),
                    o.getCheckoutRef(), o.getStatus().allowedNext(), o.getStatus() == OrderStatus.PLACED,
                    o.getCreatedAt(), o.getTrackingCarrier(), o.getTrackingNumber(),
                    Carriers.trackingUrl(o.getTrackingCarrier(), o.getTrackingNumber()), o.getShippedAt(),
                    o.getDeliveredAt(), o.getEvents().stream().map(EventDto::from).toList(), o.canReturn(),
                    o.getReturnDeadline(),
                    o.getReturns().stream().map(r -> ReturnDtos.ReturnDto.from(r, null)).toList());
        }
    }

    /** One checkout can produce several orders: one per seller. payment is null for pay on delivery. */
    public record CheckoutResponse(String checkoutRef, List<OrderDto> orders, BigDecimal total,
                                   PaymentDtos.PaymentDto payment) {
        public CheckoutResponse withPayment(PaymentDtos.PaymentDto payment) {
            return new CheckoutResponse(checkoutRef, orders, total, payment);
        }
    }
}
