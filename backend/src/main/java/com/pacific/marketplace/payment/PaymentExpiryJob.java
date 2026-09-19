package com.pacific.marketplace.payment;

import com.pacific.marketplace.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cancels card orders that were never paid, so their reserved stock goes back on sale. */
@Component
@ConditionalOnProperty(name = "app.payments.expiry-job", havingValue = "true", matchIfMissing = true)
public class PaymentExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryJob.class);

    private final PaymentService payments;

    public PaymentExpiryJob(PaymentService payments) {
        this.payments = payments;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void run() {
        int expired = payments.expireOverdue();
        if (expired > 0) log.info("Expired {} unpaid card payment(s) and released their stock.", expired);
    }
}
