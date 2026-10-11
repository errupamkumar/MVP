package com.srmecotech.ecoride.common.util;

public final class Phones {

    private Phones() {
    }

    /** "+91 98765 00001", "098765-00001" and "9876500001" are the same Indian mobile number. */
    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits;
    }

    public static boolean looksLikePhone(String login) {
        return login != null && !login.contains("@") && normalise(login).length() == 10;
    }

    /** "98765 00001" for display. */
    public static String pretty(String phone) {
        return phone != null && phone.length() == 10 ? phone.substring(0, 5) + " " + phone.substring(5) : phone;
    }
}
