package com.srmecotech.ecoride.common.util;

import java.security.SecureRandom;

public final class Codes {

    /** No 0/O, 1/I/L: the code is read out over the phone. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Codes() {
    }

    /** "ER7K2Q9M": shown on receipts and support calls. */
    public static String rideCode() {
        StringBuilder sb = new StringBuilder("ER");
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** The 4-digit code the rider tells the driver to start the trip. */
    public static String otp() {
        return String.valueOf(1000 + RANDOM.nextInt(9000));
    }

    public static String reference(String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 10; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
