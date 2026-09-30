package com.srmecotech.plantride.common.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Human-friendly identifiers. The alphabet drops look-alikes (0/O, 1/I/L). */
public final class Codes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter TRIP_DATE = DateTimeFormatter.ofPattern("MMdd");

    private Codes() {
    }

    public static String random(int length) {
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }

    /** 4-digit boarding OTP shown to the rider and typed by the driver. */
    public static String otp() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }

    public static String bookingCode() {
        return "PR-" + random(6);
    }

    public static String trackingToken() {
        return random(8);
    }

    public static String tripCode(LocalDate date) {
        return "T-" + date.format(TRIP_DATE) + "-" + random(4);
    }
}
