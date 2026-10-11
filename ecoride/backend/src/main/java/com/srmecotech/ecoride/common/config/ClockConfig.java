package com.srmecotech.ecoride.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Every service reads time from this clock, never from Instant.now(), so tests
 * can move time (past an offer timeout or the free waiting time) deterministically.
 * Business days (today's earnings, the dashboard) are counted in the city's zone.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId CITY_ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock clock(AppProperties properties) {
        Clock system = Clock.system(CITY_ZONE);
        java.time.Duration offset = properties.demo().clockOffset();
        return offset == null || offset.isZero() ? system : Clock.offset(system, offset);
    }
}
