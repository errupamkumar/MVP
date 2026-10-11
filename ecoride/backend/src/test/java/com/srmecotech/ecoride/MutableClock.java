package com.srmecotech.ecoride;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock tests can move forward (past an offer timeout, a free cancellation window, a no-show wait). */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Duration offset = Duration.ZERO;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void advance(Duration by) {
        offset = offset.plus(by);
    }

    public void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock c = new MutableClock(zone);
        c.offset = offset;
        return c;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset);
    }
}
