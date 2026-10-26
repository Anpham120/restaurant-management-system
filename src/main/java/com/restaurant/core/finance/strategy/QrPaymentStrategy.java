package com.restaurant.core.finance.strategy;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.restaurant.core.finance.dto.PaymentRequest;
import com.restaurant.core.finance.dto.PaymentResponse;
import com.restaurant.core.finance.repository.PaymentRepository;
import com.restaurant.core.finance.sepay.SepayProperties;

/**
 * QR/SePay: chỉ tạo yêu cầu chờ chuyển khoản (WAITING_TRANSFER) và trả mã tham chiếu + ảnh QR.
 * KHÔNG ghi nhận thành công ở đây — hóa đơn chỉ PAID khi webhook SePay đã xác minh gọi rc_process_verified_qr.
 */
@Component
public class QrPaymentStrategy implements PaymentStrategy {

    private final PaymentRepository payments;
    private final SepayProperties sepay;

    public QrPaymentStrategy(PaymentRepository payments, SepayProperties sepay) {
        this.payments = payments;
        this.sepay = sepay;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.QR;
    }

    @Override
    public PaymentResponse start(long invoiceId, long actorId, PaymentRequest request) {
        String reference = payments.startQr(invoiceId, actorId);
        BigDecimal amount = payments.findAmountDueByReference(reference);
        return new PaymentResponse(invoiceId, PaymentMethod.QR, "WAITING_TRANSFER", null, reference, amount, qrImageUrl(amount, reference));
    }

    /** Ảnh VietQR của SePay: https://qr.sepay.vn/img?acc=…&bank=…&amount=…&des=… (mã tham chiếu nằm trong nội dung CK). */
    private String qrImageUrl(BigDecimal amount, String reference) {
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
