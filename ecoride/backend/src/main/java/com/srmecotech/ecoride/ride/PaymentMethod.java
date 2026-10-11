package com.srmecotech.ecoride.ride;

public enum PaymentMethod {
    /** Collected by the driver; confirmed with "Cash collected". */
    CASH,
    /** Charged automatically at the end of the trip (simulated UPI collect). */
    UPI,
    /** EcoRide wallet; falls back to cash when the balance is short. */
    WALLET
}
