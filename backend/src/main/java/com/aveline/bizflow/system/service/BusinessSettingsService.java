package com.aveline.bizflow.system.service;

import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ConflictException;
import com.aveline.bizflow.document.DocumentSeries;
import com.aveline.bizflow.finance.FinancePolicy;
import com.aveline.bizflow.finance.PaymentMethod;
import com.aveline.bizflow.notification.AppNotificationService;
import com.aveline.bizflow.system.CommercialRounding;
import com.aveline.bizflow.system.SettingKeys;
import com.aveline.bizflow.system.dto.SettingsDtos.BusinessSettingsRequest;
import com.aveline.bizflow.system.dto.SettingsDtos.BusinessSettingsResponse;
import com.aveline.bizflow.system.dto.SettingsDtos.LetterheadResponse;
import com.aveline.bizflow.system.persistence.PlatformSetting;
import com.aveline.bizflow.system.persistence.PlatformSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BusinessSettingsService {

    private static final Pattern PREFIX = Pattern.compile("^[A-Z0-9]{2,8}$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> DATE_FORMATS = Set.of("medium", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd MMM yyyy");
    private static final List<String> MANAGED = List.of(
            SettingKeys.COMPANY_NAME,
            SettingKeys.COMPANY_ADDRESS,
            SettingKeys.COMPANY_PHONE,
            SettingKeys.COMPANY_EMAIL,
            SettingKeys.TAX_NAME,
            SettingKeys.TAX_NUMBER,
            SettingKeys.CURRENCY,
            SettingKeys.INVOICE_PREFIX,
            SettingKeys.PURCHASE_PREFIX,
            SettingKeys.RETURN_PREFIX,
            SettingKeys.PURCHASE_RETURN_PREFIX,
            SettingKeys.ROUNDING_RULE,
            SettingKeys.ALLOW_NEGATIVE_STOCK,
            SettingKeys.DEFAULT_PAYMENT_METHOD,
            SettingKeys.DATE_FORMAT,
            SettingKeys.DECIMAL_PRECISION,
            SettingKeys.DISPLAY_NAME);

    private final PlatformSettingRepository settingRepository;
    private final AuditRecorder auditRecorder;
    private final AppNotificationService notifications;

    public BusinessSettingsService(
            PlatformSettingRepository settingRepository,
            AuditRecorder auditRecorder,
            AppNotificationService notifications) {
        this.settingRepository = settingRepository;
        this.auditRecorder = auditRecorder;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public BusinessSettingsResponse current() {
        return response(loadManaged());
    }

    @Transactional(readOnly = true)
    public LetterheadResponse letterhead() {
        return new LetterheadResponse(
                text(SettingKeys.COMPANY_NAME, "Aveline BizFlow"),
                text(SettingKeys.COMPANY_ADDRESS, ""),
                text(SettingKeys.COMPANY_PHONE, ""),
                text(SettingKeys.COMPANY_EMAIL, ""),
                text(SettingKeys.TAX_NAME, "GSTIN"),
                text(SettingKeys.TAX_NUMBER, ""),
                text(SettingKeys.CURRENCY, "INR"),
                text(SettingKeys.DATE_FORMAT, "medium"),
                decimalPrecision());
    }

    @Transactional(readOnly = true)
    public String prefixFor(DocumentSeries series) {
        String key = switch (series) {
            case SALE -> SettingKeys.INVOICE_PREFIX;
            case PURCHASE -> SettingKeys.PURCHASE_PREFIX;
            case SALES_RETURN -> SettingKeys.RETURN_PREFIX;
            case PURCHASE_RETURN -> SettingKeys.PURCHASE_RETURN_PREFIX;
            default -> null;
        };
        if (key == null) {
            return series.prefix();
        }
        String prefix = text(key, series.prefix()).toUpperCase(Locale.ROOT);
        return PREFIX.matcher(prefix).matches() ? prefix : series.prefix();
    }

    @Transactional(readOnly = true)
    public BigDecimal round(BigDecimal amount) {
        return CommercialRounding.apply(amount, decimalPrecision(), CommercialRounding.Rule.parse(text(SettingKeys.ROUNDING_RULE, "NONE")));
    }

    @Transactional
    public BusinessSettingsResponse update(BusinessSettingsRequest request) {
        Map<String, PlatformSetting> current = loadManaged();
        long version = version(current);
        if (request.version() != version) {
            throw new ConflictException("version", "Settings were updated by someone else. Refresh and try again.");
        }
        String companyName = required(request.companyName(), "companyName", "Company name");
        String email = optional(request.email(), 160);
        if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
            throw new BusinessRuleException("EMAIL", "email", "Enter a valid email address");
        }
        String currency = required(request.currencyCode(), "currencyCode", "Currency").toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(currency).matches()) {
            throw new BusinessRuleException("CURRENCY", "currencyCode", "Currency must be a 3-letter ISO code");
        }
        String invoicePrefix = prefix(request.invoicePrefix(), "invoicePrefix", "Invoice prefix");
        String purchasePrefix = prefix(request.purchasePrefix(), "purchasePrefix", "Purchase prefix");
        String returnPrefix = prefix(request.returnPrefix(), "returnPrefix", "Return prefix");
        String purchaseReturnPrefix = prefix(request.purchaseReturnPrefix(), "purchaseReturnPrefix", "Purchase return prefix");
        CommercialRounding.Rule rounding = rounding(request.roundingRule());
        PaymentMethod method = paymentMethod(request.defaultPaymentMethod());
        FinancePolicy.requireSettlementMethod(method);
        String dateFormat = required(request.dateFormat(), "dateFormat", "Date format");
        if (!DATE_FORMATS.contains(dateFormat)) {
            throw new BusinessRuleException("DATE_FORMAT", "dateFormat", "Choose a supported date format");
        }
        int precision = request.decimalPrecision();
        Map<String, String> next = new LinkedHashMap<>();
        next.put(SettingKeys.COMPANY_NAME, companyName);
        next.put(SettingKeys.DISPLAY_NAME, companyName);
        next.put(SettingKeys.COMPANY_ADDRESS, optional(request.companyAddress(), 500));
        next.put(SettingKeys.COMPANY_PHONE, optional(request.phone(), 32));
        next.put(SettingKeys.COMPANY_EMAIL, email);
        next.put(SettingKeys.TAX_NAME, optional(request.taxName(), 80));
        next.put(SettingKeys.TAX_NUMBER, optional(request.taxNumber(), 64));
        next.put(SettingKeys.CURRENCY, currency);
        next.put(SettingKeys.INVOICE_PREFIX, invoicePrefix);
        next.put(SettingKeys.PURCHASE_PREFIX, purchasePrefix);
        next.put(SettingKeys.RETURN_PREFIX, returnPrefix);
        next.put(SettingKeys.PURCHASE_RETURN_PREFIX, purchaseReturnPrefix);
        next.put(SettingKeys.ROUNDING_RULE, rounding.name());
        next.put(SettingKeys.ALLOW_NEGATIVE_STOCK, Boolean.TRUE.equals(request.allowNegativeStock()) ? "true" : "false");
        next.put(SettingKeys.DEFAULT_PAYMENT_METHOD, method.name());
        next.put(SettingKeys.DATE_FORMAT, dateFormat);
        next.put(SettingKeys.DECIMAL_PRECISION, Integer.toString(precision));

        Map<String, String> before = new LinkedHashMap<>();
        Map<String, String> after = new LinkedHashMap<>();
        for (Map.Entry<String, String> change : next.entrySet()) {
            PlatformSetting setting = current.get(change.getKey());
            if (setting == null) {
                throw new IllegalStateException("Missing setting " + change.getKey());
            }
            if (!setting.getSettingValue().equals(change.getValue())) {
                before.put(change.getKey(), setting.getSettingValue());
                after.put(change.getKey(), change.getValue());
                setting.revise(change.getValue());
            }
        }
        if (!before.isEmpty()) {
            auditRecorder.recordChange(
                    "SETTINGS_UPDATED",
                    "settings",
                    "settings",
                    "business",
                    "Business settings updated",
                    json(before),
                    json(after));
            notifications.system("Settings updated", "Business settings were changed.", "settings");
        }
        return response(loadManaged());
    }

    private BusinessSettingsResponse response(Map<String, PlatformSetting> settings) {
        return new BusinessSettingsResponse(
                value(settings, SettingKeys.COMPANY_NAME),
                value(settings, SettingKeys.COMPANY_ADDRESS),
                value(settings, SettingKeys.COMPANY_PHONE),
                value(settings, SettingKeys.COMPANY_EMAIL),
                value(settings, SettingKeys.TAX_NAME),
                value(settings, SettingKeys.TAX_NUMBER),
                value(settings, SettingKeys.CURRENCY),
                value(settings, SettingKeys.INVOICE_PREFIX),
                value(settings, SettingKeys.PURCHASE_PREFIX),
                value(settings, SettingKeys.RETURN_PREFIX),
                value(settings, SettingKeys.PURCHASE_RETURN_PREFIX),
                value(settings, SettingKeys.ROUNDING_RULE),
                "true".equalsIgnoreCase(value(settings, SettingKeys.ALLOW_NEGATIVE_STOCK)),
                value(settings, SettingKeys.DEFAULT_PAYMENT_METHOD),
                value(settings, SettingKeys.DATE_FORMAT),
                parsePrecision(value(settings, SettingKeys.DECIMAL_PRECISION)),
                version(settings));
    }

    private Map<String, PlatformSetting> loadManaged() {
        Map<String, PlatformSetting> settings = new LinkedHashMap<>();
        for (String key : MANAGED) {
            settings.put(key, settingRepository.findBySettingKey(key)
                    .orElseThrow(() -> new IllegalStateException("Missing setting " + key)));
        }
        return settings;
    }

    private long version(Map<String, PlatformSetting> settings) {
        long version = 0;
        for (PlatformSetting setting : settings.values()) {
            if (setting.getVersion() != null && setting.getVersion() > version) {
                version = setting.getVersion();
            }
        }
        return version;
    }

    private String value(Map<String, PlatformSetting> settings, String key) {
        PlatformSetting setting = settings.get(key);
        return setting == null || setting.getSettingValue() == null ? "" : setting.getSettingValue();
    }

    private String text(String key, String fallback) {
        return settingRepository.findBySettingKey(key)
                .map(PlatformSetting::getSettingValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
    }

    private int decimalPrecision() {
        return parsePrecision(text(SettingKeys.DECIMAL_PRECISION, "2"));
    }

    private int parsePrecision(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0 || value > 4) {
                return 2;
            }
            return value;
        } catch (NumberFormatException ex) {
            return 2;
        }
    }

    private String required(String value, String field, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("REQUIRED", field, label + " is required");
        }
        return value.trim();
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new BusinessRuleException("LENGTH", "value", "Value is too long");
        }
        return trimmed;
    }

    private String prefix(String value, String field, String label) {
        String normalized = required(value, field, label).toUpperCase(Locale.ROOT);
        if (!PREFIX.matcher(normalized).matches()) {
            throw new BusinessRuleException("PREFIX", field, label + " must be 2 to 8 letters or digits");
        }
        return normalized;
    }

    private CommercialRounding.Rule rounding(String value) {
        try {
            return CommercialRounding.Rule.valueOf(required(value, "roundingRule", "Rounding rule").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("ROUNDING", "roundingRule", "Choose none, nearest unit, or nearest tenth");
        }
    }

    private PaymentMethod paymentMethod(String value) {
        try {
            return PaymentMethod.valueOf(required(value, "defaultPaymentMethod", "Default payment method").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("PAYMENT_METHOD", "defaultPaymentMethod", "Choose a supported payment method");
        }
    }

    private String json(Map<String, String> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        json.append('}');
        return json.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
