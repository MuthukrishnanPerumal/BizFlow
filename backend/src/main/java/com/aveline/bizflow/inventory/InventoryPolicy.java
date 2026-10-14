package com.aveline.bizflow.inventory;

import com.aveline.bizflow.system.SettingKeys;
import com.aveline.bizflow.system.persistence.PlatformSettingRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryPolicy {

    static final String ALLOW_NEGATIVE = SettingKeys.ALLOW_NEGATIVE_STOCK;
    static final String VALUATION_METHOD = "inventory.valuation-method";

    private final PlatformSettingRepository settingRepository;

    public InventoryPolicy(PlatformSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public boolean allowNegativeStock() {
        return settingRepository.findBySettingKey(ALLOW_NEGATIVE)
                .map(setting -> "true".equalsIgnoreCase(setting.getSettingValue().trim()))
                .orElse(false);
    }

    public ValuationMethod valuationMethod() {
        return settingRepository.findBySettingKey(VALUATION_METHOD)
                .map(setting -> parse(setting.getSettingValue()))
                .orElse(ValuationMethod.AVERAGE);
    }

    private ValuationMethod parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ValuationMethod.AVERAGE;
        }
        try {
            return ValuationMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ValuationMethod.AVERAGE;
        }
    }
}
