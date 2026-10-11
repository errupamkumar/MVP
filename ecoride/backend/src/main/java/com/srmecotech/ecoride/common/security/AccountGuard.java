package com.srmecotech.ecoride.common.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A blocked rider or a removed account loses access on its next request, not when its
 * token expires. A suspended or pending driver keeps access so the app can explain why
 * it cannot go online.
 */
@Component
public class AccountGuard {

    private final JdbcTemplate jdbc;

    public AccountGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isAllowed(AuthenticatedUser user) {
        String sql = switch (user.role()) {
            case RIDER -> "SELECT COUNT(*) FROM rider WHERE id = ? AND status = 'ACTIVE'";
            case DRIVER -> "SELECT COUNT(*) FROM driver WHERE id = ? AND status <> 'REJECTED'";
            case ADMIN -> "SELECT COUNT(*) FROM admin_user WHERE id = ?";
        };
        Integer count = jdbc.queryForObject(sql, Integer.class, user.id());
        return count != null && count > 0;
    }
}
