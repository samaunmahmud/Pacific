package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.ReturnItem;
import com.pacific.marketplace.domain.ReturnReason;
import com.pacific.marketplace.domain.ReturnRequest;
import com.pacific.marketplace.domain.ReturnStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record ReturnLine(@NotNull(message = "Choose an item to return.") Long orderItemId,
                             @Min(value = 1, message = "Choose how many to return.") @Max(1000) int quantity) {
    }

    public record ReturnRequestBody(
            @NotEmpty(message = "Choose at least one item to return.") @Size(max = 50) List<@Valid ReturnLine> items,
            @NotNull(message = "Please choose a reason.") ReturnReason reason,
            @Size(max = 500, message = "Please keep it under 500 characters.") String comment) {
    }

    public record DecisionRequest(@Size(max = 300, message = "Please keep the note under 300 characters.") String note) {
    }

    /** amount: leave out to refund the most that is allowed. restock: put the goods back on sale (default yes). */
    public record RefundRequest(
            @DecimalMin(value = "0.01", message = "The refund must be at least 0.01.")
            @Digits(integer = 8, fraction = 2, message = "Use pounds and pence, for example 12.50.") BigDecimal amount,
            Boolean restock,
            @Size(max = 300, message = "Please keep the note under 300 characters.") String note) {
    }

    public record ReturnItemDto(Long orderItemId, String productName, int quantity, BigDecimal unitPrice,
                                BigDecimal lineTotal) {
        static ReturnItemDto from(ReturnItem i) {
            var item = i.getOrderItem();
            return new ReturnItemDto(item.getId(), item.getProductName(), i.getQuantity(), item.getUnitPrice(),
                    item.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
        }
    }

    /**
     * maxRefund is only filled in for an approved return (what the seller may refund). paymentMethod tells the
     * screen whether the money goes back to a card or is settled by the seller directly.
     */
    public record ReturnDto(Long id, Long orderId, ReturnStatus status, ReturnReason reason, String reasonLabel,
                            String comment, String sellerNote, BigDecimal refundAmount, boolean restocked,
                            List<ReturnItemDto> items, BigDecimal itemsValue, BigDecimal maxRefund,
                            String paymentMethod, String customerName, String sellerName, Instant createdAt,
                            Instant resolvedAt) {
        public static ReturnDto from(ReturnRequest r, BigDecimal maxRefund) {
            Order o = r.getOrder();
            return new ReturnDto(r.getId(), o.getId(), r.getStatus(), r.getReason(), r.getReason().label(),
                    r.getComment(), r.getSellerNote(), r.getRefundAmount(), r.isRestocked(),
                    r.getItems().stream().map(ReturnItemDto::from).toList(), r.itemsValue(), maxRefund,
                    o.getPaymentMethod(), o.getUser().getName(),
                    o.getSeller() == null ? ProductDtos.ProductDto.HOUSE_STORE : o.getSeller().getStoreName(),
                    r.getCreatedAt(), r.getResolvedAt());
        }
    }
}
