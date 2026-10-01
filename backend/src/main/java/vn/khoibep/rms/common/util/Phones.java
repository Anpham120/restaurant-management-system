package vn.khoibep.rms.common.util;

/** BR-44: one way to write a Vietnamese phone number, so the same guest is found whatever way it was typed. */
public final class Phones {

    private Phones() {
    }

    /** Digits only, +84 or 84 in front becomes 0: ten digits from 0, or null when it is no such number. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("84")) {
            digits = "0" + digits.substring(2);
        }
        return digits.matches("0[0-9]{9}") ? digits : null;
    }
}
