package com.srmecotech.ecoride.common.settings;

import com.srmecotech.ecoride.common.error.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Settings are read on every GPS ping and dispatch sweep, so values are cached in memory
 * and reloaded when the admin changes one (or the demo is reset). A missing or malformed
 * row falls back to the enum default, so a bad value never stops dispatch.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private volatile Map<Setting, String> cache;

    public SettingsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public int getInt(Setting setting) {
        try {
            return (int) Math.round(Double.parseDouble(raw(setting)));
        } catch (NumberFormatException ex) {
            log.warn("Setting {} has a non-numeric value; using the default", setting);
            return (int) Math.round(Double.parseDouble(setting.defaultValue()));
        }
    }

    public double getDouble(Setting setting) {
        try {
            return Double.parseDouble(raw(setting));
        } catch (NumberFormatException ex) {
            log.warn("Setting {} has a non-numeric value; using the default", setting);
            return Double.parseDouble(setting.defaultValue());
        }
    }

    public boolean getBool(Setting setting) {
        return Boolean.parseBoolean(raw(setting).trim());
    }

    /** Every setting with its current value, in declaration order. */
    public Map<Setting, String> all() {
        Map<Setting, String> result = new LinkedHashMap<>();
        Map<Setting, String> values = values();
        for (Setting s : Setting.values()) {
            result.put(s, values.getOrDefault(s, s.defaultValue()));
        }
        return result;
    }

    /** Validates against the setting's type and bounds, stores it, and returns the normalised value. */
    public String update(Setting setting, String value) {
        String normalised = normalise(setting, value);
        jdbc.update("""
                INSERT INTO app_setting (setting_key, setting_value, updated_at) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = VALUES(updated_at)
                """, setting.name(), normalised, Timestamp.from(clock.instant()));
        reload();
        return normalised;
    }

    /** Writes every default (demo seed). */
    public void seedDefaults() {
        Timestamp now = Timestamp.from(clock.instant());
        for (Setting s : Setting.values()) {
            jdbc.update("INSERT INTO app_setting (setting_key, setting_value, updated_at) VALUES (?, ?, ?)",
                    s.name(), s.defaultValue(), now);
        }
        reload();
    }

    /** Every setting back to its default. */
    public void resetToDefaults() {
        jdbc.update("DELETE FROM app_setting");
        seedDefaults();
    }

    public void reload() {
        cache = null;
    }

    private String raw(Setting setting) {
        String value = values().get(setting);
        return value == null ? setting.defaultValue() : value;
    }

    private Map<Setting, String> values() {
        Map<Setting, String> current = cache;
        if (current == null) {
            Map<Setting, String> loaded = new EnumMap<>(Setting.class);
            jdbc.query("SELECT setting_key, setting_value FROM app_setting", rs -> {
                try {
                    loaded.put(Setting.valueOf(rs.getString(1)), rs.getString(2));
                } catch (IllegalArgumentException ignored) {
                    // A key from an older version: not ours any more.
                }
            });
            cache = loaded;
            current = loaded;
        }
        return current;
    }

    private static String normalise(Setting setting, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("SETTING_INVALID", setting.label() + " needs a value.");
        }
        String trimmed = value.trim();
        if (setting.type() == Setting.Type.BOOL) {
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!lower.equals("true") && !lower.equals("false")) {
                throw new InvalidRequestException("SETTING_INVALID", setting.label() + " must be true or false.");
            }
            return lower;
        }
        double number;
        try {
            number = Double.parseDouble(trimmed);
        } catch (NumberFormatException ex) {
            throw new InvalidRequestException("SETTING_INVALID", setting.label() + " must be a number.");
        }
        if (number < setting.min() || number > setting.max()) {
            throw new InvalidRequestException("SETTING_INVALID", "%s must be between %s and %s %s."
                    .formatted(setting.label(), plain(setting.min()), plain(setting.max()), setting.unit()).trim());
        }
        if (setting.type() == Setting.Type.INT) {
            return String.valueOf(Math.round(number));
        }
        return plain(number);
    }

    private static String plain(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }
}
