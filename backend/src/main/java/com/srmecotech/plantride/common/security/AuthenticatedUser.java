package com.srmecotech.plantride.common.security;

import com.srmecotech.plantride.identity.Role;

/** The principal placed in the security context for every authenticated request. */
public record AuthenticatedUser(Long id, String username, Role role, String fullName) {
}
