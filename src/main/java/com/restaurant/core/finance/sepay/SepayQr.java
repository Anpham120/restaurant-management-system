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

    /**
     * null nếu chưa cấu hình tài khoản nhận (SEPAY_BANK_*). Số tiền phải là số nguyên đồng: toBigIntegerExact() báo lỗi
     * thay vì cắt phần lẻ, vì QR in 10000 cho hóa đơn 10000,50 sẽ không bao giờ khớp khi webhook đối chiếu số tiền.
     */
    public String imageUrl(BigDecimal amount, String reference) {
        if (!sepay.qrConfigured()) {
            return null;
        }
        return "https://qr.sepay.vn/img?acc=" + urlEncode(sepay.bankAccount()) + "&bank=" + urlEncode(sepay.bankName())
                + "&amount=" + amount.toBigIntegerExact() + "&des=" + urlEncode(reference);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
