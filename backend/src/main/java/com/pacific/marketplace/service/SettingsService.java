package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.Setting;
import com.pacific.marketplace.repo.SettingRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    static final String COMMISSION_KEY = "commission.default_percent";
    private static final BigDecimal FALLBACK_COMMISSION = new BigDecimal("10.00");

    private final SettingRepository settings;

    public SettingsService(SettingRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public BigDecimal defaultCommission() {
        return settings.findById(COMMISSION_KEY).map(s -> new BigDecimal(s.getValue())).orElse(FALLBACK_COMMISSION);
    }

    @Transactional
    public BigDecimal setDefaultCommission(BigDecimal percent) {
        BigDecimal value = percent.setScale(2, java.math.RoundingMode.HALF_UP);
        Setting setting = settings.findById(COMMISSION_KEY).orElseGet(() -> new Setting(COMMISSION_KEY, "0"));
        setting.setValue(value.toPlainString());
        settings.save(setting);
        return value;
    }

    /** The commission percent that applies to this seller right now: their override, else the default. */
    @Transactional(readOnly = true)
    public BigDecimal effectiveCommission(SellerProfile seller) {
        return seller.getCommissionOverride() != null ? seller.getCommissionOverride() : defaultCommission();
    }
}
