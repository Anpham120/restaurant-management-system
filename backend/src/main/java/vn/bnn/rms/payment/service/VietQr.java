package vn.bnn.rms.payment.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Image link from the public VietQR quick-link service; any Vietnamese banking app can scan it. */
public final class VietQr {

    private VietQr() {
    }

    public static String imageUrl(String bankCode, String accountNo, String accountName, long amount,
                                  String content) {
        return "https://img.vietqr.io/image/" + encode(bankCode) + "-" + encode(accountNo) + "-compact2.png"
                + "?amount=" + amount
                + "&addInfo=" + encode(content)
                + "&accountName=" + encode(accountName);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
