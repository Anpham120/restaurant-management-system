package com.restaurant.core.finance.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.restaurant.core.finance.dto.PaymentRequest;
import com.restaurant.core.finance.dto.PaymentResponse;
import com.restaurant.core.finance.repository.PaymentRepository;
import com.restaurant.core.finance.sepay.SepayQr;

/**
 * QR/SePay: chỉ tạo yêu cầu chờ chuyển khoản (WAITING_TRANSFER) và trả mã tham chiếu + ảnh QR.
 * KHÔNG ghi nhận thành công ở đây — hóa đơn chỉ PAID khi webhook SePay đã xác minh gọi rc_process_verified_qr.
 */
@Component
public class QrPaymentStrategy implements PaymentStrategy {

    private final PaymentRepository payments;
    private final SepayQr qr;

    public QrPaymentStrategy(PaymentRepository payments, SepayQr qr) {
        this.payments = payments;
        this.qr = qr;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.QR;
    }

    @Override
    public PaymentResponse start(long invoiceId, long actorId, PaymentRequest request) {
        String reference = payments.startQr(invoiceId, actorId);
        BigDecimal amount = payments.findAmountDueByReference(reference);
        return new PaymentResponse(invoiceId, PaymentMethod.QR, "WAITING_TRANSFER", null, reference, amount, qr.imageUrl(amount, reference));
    }
}
