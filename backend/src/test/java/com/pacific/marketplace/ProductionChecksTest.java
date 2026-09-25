package com.pacific.marketplace;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.config.ProductionChecks;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A real shop refuses to start with development-only settings on. */
class ProductionChecksTest {

    private static AppProperties props(boolean production, boolean simulator, String publicUrl, List<String> origins, String stripeKey) {
        return new AppProperties(new AppProperties.Jwt("x".repeat(40), 12), new AppProperties.Cors(origins),
                new AppProperties.Reviews(5), new AppProperties.Shop("GBP", new BigDecimal("3.99"), new BigDecimal("50"), 10, 30),
                publicUrl, new AppProperties.Payments(35, false, new AppProperties.Payments.Stripe(stripeKey, "", ""), simulator),
                new AppProperties.Mail("Pacific <a@b.c>", false), new AppProperties.Security(60, 48, 5, 15, 50, production), null, null, null);
    }

    @Test
    void aDevelopmentSetupIsLeftAloneWhenProductionIsOff() {
        var checks = new ProductionChecks(props(false, true, "http://localhost:5173", List.of("http://localhost:5173"), ""),
                new MockEnvironment().withProperty("app.demo-data.enabled", "true"));
        assertThatCode(checks::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void productionRefusesToStartWithTheSimulatorDemoDataOrLocalUrls() {
        var checks = new ProductionChecks(props(true, true, "http://localhost:5173", List.of("http://localhost:5173"), "sk_x"),
                new MockEnvironment().withProperty("app.demo-data.enabled", "true"));
        assertThatThrownBy(checks::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENTS_SIMULATOR_ENABLED").hasMessageContaining("DEMO_DATA")
                .hasMessageContaining("PUBLIC_URL").hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void aProperlyConfiguredProductionShopStartsAndOnlyWarnsAboutMissingMailOrCardSettings() {
        var checks = new ProductionChecks(props(true, false, "https://shop.example.com", List.of("https://shop.example.com"), ""),
                new MockEnvironment());
        assertThat(checks.problems()).isEmpty();
        assertThatCode(checks::afterPropertiesSet).doesNotThrowAnyException();
    }
}
