package com.srmecotech.plantride.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** The real clock plus an adjustable offset, so a test can jump past a wait (e.g. the no-show rule). */
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
        MutableClock copy = new MutableClock(zone);
        copy.offset = offset;
        return copy;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset);
    }
}
