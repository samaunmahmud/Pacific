package com.pacific.marketplace.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * With APP_PRODUCTION=true the app refuses to start if a development-only setting is on, instead of quietly running
 * a real shop with a fake payment page or invented customers. Settings that are merely incomplete (no mail server, no
 * card provider) only produce a warning, since the shop still works.
 */
@Component
public class ProductionChecks implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionChecks.class);

    private final AppProperties props;
    private final Environment env;

    public ProductionChecks(AppProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        if (props.security() == null || !props.security().production()) return;
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("APP_PRODUCTION is on, but this configuration isn't safe for a real shop:\n - "
                    + String.join("\n - ", problems));
        }
        for (String warning : warnings()) log.warn("Production check: {}", warning);
        log.info("Production checks passed.");
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (props.payments() != null && props.payments().simulatorEnabled()) {
            problems.add("PAYMENTS_SIMULATOR_ENABLED must be false: the simulator is a fake payment page that takes no money.");
        }
        if (env.getProperty("app.demo-data.enabled", Boolean.class, false)) {
            problems.add("DEMO_DATA must be false: it fills the shop with invented products, shoppers and reviews.");
        }
        String url = props.publicUrl();
        if (!StringUtils.hasText(url) || !url.startsWith("https://")) {
            problems.add("PUBLIC_URL must be the shop's https address (it is used in payment redirects and email links), not " + url + ".");
        }
        if (props.cors() != null && props.cors().allowedOrigins() != null
                && props.cors().allowedOrigins().stream().anyMatch(o -> o.contains("localhost") || o.contains("127.0.0.1"))) {
            problems.add("CORS_ALLOWED_ORIGINS must not include localhost.");
        }
        return problems;
    }

    List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        if (!StringUtils.hasText(env.getProperty("spring.mail.host"))) {
            warnings.add("MAIL_HOST isn't set, so no emails are sent: no order confirmations, receipts or password resets.");
        }
        if (props.payments() == null || props.payments().stripe() == null || !props.payments().stripe().configured()) {
            warnings.add("STRIPE_SECRET_KEY isn't set, so card payments are switched off (pay on delivery only).");
        }
        return warnings;
    }
}
