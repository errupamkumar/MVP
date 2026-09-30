package com.srmecotech.plantride.common.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plant-wide rules that LHS changes without a release. Each getter has a
 * safe default, so a missing or malformed row never stops dispatch.
 */
@Service
public class SettingsService {

    public static final String COST_SHARING_RULE = "COST_SHARING_RULE";
    public static final String NO_SHOW_PAUSE_COUNT = "NO_SHOW_PAUSE_COUNT";
    public static final String NO_SHOW_WINDOW_DAYS = "NO_SHOW_WINDOW_DAYS";
    public static final String DISPATCH_LEAD_MINUTES = "DISPATCH_LEAD_MINUTES";
    public static final String MAX_ADVANCE_BOOKING_DAYS = "MAX_ADVANCE_BOOKING_DAYS";
    public static final String OTP_MAX_ATTEMPTS = "OTP_MAX_ATTEMPTS";
    public static final String DOC_EXPIRY_ALERT_DAYS = "DOC_EXPIRY_ALERT_DAYS";
    public static final String REQUEST_EXPIRY_MINUTES = "REQUEST_EXPIRY_MINUTES";

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return repository.findById(key)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getValue().trim());
                    } catch (NumberFormatException ex) {
                        log.warn("Setting {} has a non-numeric value '{}'; using {}", key, s.getValue(), defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public String getString(String key, String defaultValue) {
        return repository.findById(key).map(AppSetting::getValue).orElse(defaultValue);
    }

    public CostSharingRule costSharingRule() {
        try {
            return CostSharingRule.valueOf(getString(COST_SHARING_RULE, "DISTANCE").trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return CostSharingRule.DISTANCE;
        }
    }

    public enum CostSharingRule {
        /** Every rider on the trip pays the same share. */
        EQUAL,
        /** Each rider pays in proportion to the kilometres they rode (times seats). */
        DISTANCE
    }
}
