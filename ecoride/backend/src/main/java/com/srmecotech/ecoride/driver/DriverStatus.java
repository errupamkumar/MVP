package com.srmecotech.ecoride.driver;

/** Onboarding and compliance state, set by the admin console. */
public enum DriverStatus {
    /** Signed up; documents and vehicle not yet checked. Can sign in, cannot go online. */
    PENDING_APPROVAL,
    APPROVED,
    /** Application turned down: the account cannot sign in. */
    REJECTED,
    /** Temporarily barred (complaints, expired documents): can sign in, cannot go online. */
    SUSPENDED
}
