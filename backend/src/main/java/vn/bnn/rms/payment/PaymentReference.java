package vn.bnn.rms.payment;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payment code written in the transfer content (BR-14): "BNN" + 8 characters without look-alikes (0/O, 1/I).
 * Banks may add text, change case or insert spaces, so matching ignores everything but letters and digits.
 */
public final class PaymentReference {

    static final String PREFIX = "BNN";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final Pattern PATTERN = Pattern.compile(PREFIX + "[A-Z0-9]{" + LENGTH + "}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PaymentReference() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Every possible code found in the given texts, in order, overlapping matches included. */
    public static List<String> candidates(String... texts) {
        List<String> result = new ArrayList<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String normalized = text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            Matcher matcher = PATTERN.matcher(normalized);
            int from = 0;
            while (from < normalized.length() && matcher.find(from)) {
                if (!result.contains(matcher.group())) {
                    result.add(matcher.group());
                }
                from = matcher.start() + 1;
            }
        }
        return result;
    }
}
