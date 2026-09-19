package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Payment;
import com.pacific.marketplace.domain.PaymentProviderType;
import com.pacific.marketplace.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** checkoutUrl is only present while the payment can still be completed. */
    public record PaymentDto(String ref, PaymentStatus status, PaymentProviderType provider, BigDecimal amount,
                             String currency, String checkoutUrl, Instant expiresAt, BigDecimal refundedAmount,
                             boolean simulator) {
        public static PaymentDto from(Payment p) {
            return new PaymentDto(p.getCheckoutRef(), p.getStatus(), p.getProvider(), p.getAmount(), p.getCurrency(),
                    p.getStatus() == PaymentStatus.PENDING ? p.getCheckoutUrl() : null, p.getExpiresAt(),
                    p.getRefundedAmount(), p.getProvider() == PaymentProviderType.SIMULATOR);
        }
    }

    /** What the checkout page needs to know: whether to offer card payment, and whether it is only a test. */
    public record PaymentConfigDto(boolean cardEnabled, boolean simulator) {
    }

    public enum SimulatedOutcome { PAID, CANCELLED }

    public record SimulateRequest(@NotNull(message = "Outcome is required.") SimulatedOutcome outcome) {
    }
}
