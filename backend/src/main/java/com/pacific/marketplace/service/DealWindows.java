package com.pacific.marketplace.service;

import com.pacific.marketplace.repo.LightningDealRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** When a Lightning Deal starts or ends, its product page's buy box (and search price) is worked out again. */
@Component
public class DealWindows {

    private final LightningDealRepository deals;
    private final BuyBox buyBox;
    private Instant last = Instant.now().minusSeconds(120);

    public DealWindows(LightningDealRepository deals, BuyBox buyBox) {
        this.deals = deals;
        this.buyBox = buyBox;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 5_000)
    @Transactional
    public void refresh() {
        Instant now = Instant.now();
        buyBox.refreshFor(deals.findProductsWithWindowChange(last, now));
        last = now;
    }
}
