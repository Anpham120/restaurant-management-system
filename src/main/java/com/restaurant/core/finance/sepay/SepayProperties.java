package com.restaurant.core.finance.sepay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình SePay (biến môi trường, không ghi vào file):
 * - apiKey: khóa API cấu hình ở mục webhook của SePay; SePay gửi header "Authorization: Apikey &lt;apiKey&gt;".
 * - bankAccount, bankName: tài khoản nhận để dựng ảnh QR qua https://qr.sepay.vn/img (bankName theo qr.sepay.vn/banks.json).
 * - webhookDb: login PostgreSQL thuộc role rc_webhook (chỉ được EXECUTE rc_process_verified_qr — docs/09-acl-v4.1.sql).
 */
@ConfigurationProperties("app.sepay")
public record SepayProperties(String apiKey, String bankAccount, String bankName, WebhookDb webhookDb) {

    public record WebhookDb(String url, String username, String password) {
    }

    public boolean qrConfigured() {
        return hasText(bankAccount) && hasText(bankName);
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
