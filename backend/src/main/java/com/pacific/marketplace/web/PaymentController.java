package com.pacific.marketplace.web;

import com.pacific.marketplace.payment.StripeWebhook;
import com.pacific.marketplace.service.PaymentService;
import com.pacific.marketplace.web.dto.PaymentDtos.PaymentConfigDto;
import com.pacific.marketplace.web.dto.PaymentDtos.PaymentDto;
import com.pacific.marketplace.web.dto.PaymentDtos.SimulateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;
    private final StripeWebhook stripeWebhook;

    public PaymentController(PaymentService payments, StripeWebhook stripeWebhook) {
        this.payments = payments;
        this.stripeWebhook = stripeWebhook;
    }

    @GetMapping("/config")
    public PaymentConfigDto config() {
        return payments.config();
    }

    /** The customer's own payment; also checks with the provider if it is still pending. */
    @GetMapping("/{ref}")
    public PaymentDto get(@PathVariable String ref, @AuthenticationPrincipal Jwt jwt) {
        return payments.get(ref, CurrentUser.id(jwt));
    }

    @PostMapping("/{ref}/cancel")
    public PaymentDto cancel(@PathVariable String ref, @AuthenticationPrincipal Jwt jwt) {
        return payments.cancel(ref, CurrentUser.id(jwt));
    }

    /** Test-mode simulator only (404 for real payments). */
    @PostMapping("/{ref}/simulate")
    public PaymentDto simulate(@PathVariable String ref, @Valid @RequestBody SimulateRequest req,
                               @AuthenticationPrincipal Jwt jwt) {
        return payments.simulate(ref, CurrentUser.id(jwt), req.outcome());
    }

    /** Called by Stripe, not by browsers: public, but every request must carry a valid signature. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] body,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        stripeWebhook.handle(body, signature);
        return ResponseEntity.ok().build();
    }
}
