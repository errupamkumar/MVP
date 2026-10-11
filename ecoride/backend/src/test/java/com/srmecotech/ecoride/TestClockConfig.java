package com.srmecotech.ecoride;

import com.srmecotech.ecoride.common.config.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/** Replaces the system clock with one the tests can move forward. */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public Clock testClock() {
        return new MutableClock(ClockConfig.CITY_ZONE);
    }
}
