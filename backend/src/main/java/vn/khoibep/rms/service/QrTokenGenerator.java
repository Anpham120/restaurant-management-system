package vn.khoibep.rms.service;

import java.security.SecureRandom;
import java.util.Base64;

/** BR-09: 128 random bits, URL-safe, so a table's QR address cannot be guessed. */
public final class QrTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private QrTokenGenerator() {
    }

    public static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
