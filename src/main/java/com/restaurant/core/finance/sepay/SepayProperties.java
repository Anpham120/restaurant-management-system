package com.restaurant.core.finance.sepay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình SePay (biến môi trường, không ghi vào file):
 * - apiKey: khóa API cấu hình ở mục webhook của SePay; SePay gửi header "Authorization: Apikey &lt;apiKey&gt;".
 * - bankAccount, bankName: tài khoản nhận để dựng ảnh QR qua https://qr.sepay.vn/img (bankName theo qr.sepay.vn/banks.json).
 */
@ConfigurationProperties("app.sepay")
public record SepayProperties(String apiKey, String bankAccount, String bankName) {

    public boolean qrConfigured() {
        return hasText(bankAccount) && hasText(bankName);
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
