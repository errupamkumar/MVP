package com.srmecotech.ecoride.common.security;

/**
 * The principal placed in the security context for every authenticated request.
 * {@code id} is the row id in the table of the account's {@code role}.
 */
public record AuthenticatedUser(Long id, Role role, String name) {

    /** "Rider #12 Ananya Rao": who did it, for the audit log and ride timeline. */
    public String label() {
        return switch (role) {
            case RIDER -> "Rider " + name;
            case DRIVER -> "Driver " + name;
            case ADMIN -> "Admin " + name;
        };
    }
}
