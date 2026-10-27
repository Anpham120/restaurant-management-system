package com.restaurant.core.finance.sepay;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/** Ảnh VietQR của SePay: https://qr.sepay.vn/img?acc=…&bank=…&amount=…&des=… (mã tham chiếu nằm trong nội dung CK). */
@Component
public class SepayQr {

    private final SepayProperties sepay;

    public SepayQr(SepayProperties sepay) {
        this.sepay = sepay;
    }

    /** null nếu chưa cấu hình tài khoản nhận (SEPAY_BANK_*). */
    public String imageUrl(BigDecimal amount, String reference) {
        if (!sepay.qrConfigured()) {
            return null;
        }
        return "https://qr.sepay.vn/img?acc=" + enc(sepay.bankAccount()) + "&bank=" + enc(sepay.bankName())
                + "&amount=" + amount.toBigInteger() + "&des=" + enc(reference);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
