package com.srmecotech.plantride.support;

import com.srmecotech.plantride.common.security.JsonSecurityErrorHandlers;
import com.srmecotech.plantride.common.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

/**
 * Slice-test wiring: the real security chain (JWT filter, role rules, JSON
 * 401/403 bodies) and a fixed clock. The JwtService and user repository
 * are mocked per test, so tokens are just labels like "rider".
 */
@TestConfiguration
@Import({SecurityConfig.class, JsonSecurityErrorHandlers.class})
public class WebMvcTestSupport {

    @Bean
    Clock clock() {
        return Fixtures.CLOCK;
    }
}
