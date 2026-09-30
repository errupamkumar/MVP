package com.srmecotech.plantride.identity;

public enum Role {
    /** Books, tracks and rates rides (also books for guests as their host). */
    EMPLOYEE,
    /** Runs the sequenced trip queue, OTP boarding, issue reports. */
    DRIVER,
    /** Transport desk: live board, approvals, overrides, alerts. */
    DESK,
    /** Fleet admin & finance: everything the desk does plus routes, rules and billing. */
    ADMIN
}
