package com.srmecotech.plantride.booking;

import java.util.EnumSet;
import java.util.Set;

/**
 * <pre>
 *  SCHEDULED ──(lead time)──► REQUESTED ──(matched)──► ASSIGNED ──(OTP)──► ONBOARD ──(drop)──► COMPLETED
 *  PENDING_APPROVAL ──(desk approves)──► REQUESTED        │
 *        └──(desk rejects)──► REJECTED                    ├──(driver waited, rider absent)──► NO_SHOW
 *  any state before ONBOARD ──(rider/desk cancels)──► CANCELLED
 * </pre>
 */
public enum BookingStatus {
    SCHEDULED,
    PENDING_APPROVAL,
    REQUESTED,
    ASSIGNED,
    ONBOARD,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    REJECTED;

    /** Bookings still in play (shown under "Upcoming"). */
    public static final Set<BookingStatus> ACTIVE = EnumSet.of(SCHEDULED, PENDING_APPROVAL, REQUESTED, ASSIGNED, ONBOARD);

    /** Bookings that occupy seats on a trip. */
    public static final Set<BookingStatus> ON_TRIP = EnumSet.of(ASSIGNED, ONBOARD);

    /** Bookings that have left the dispatch flow for good. */
    public static final Set<BookingStatus> CLOSED = EnumSet.of(COMPLETED, CANCELLED, NO_SHOW, REJECTED);

    /** Cancellable by the rider: anything before boarding. */
    public static final Set<BookingStatus> CANCELLABLE = EnumSet.of(SCHEDULED, PENDING_APPROVAL, REQUESTED, ASSIGNED);
}
