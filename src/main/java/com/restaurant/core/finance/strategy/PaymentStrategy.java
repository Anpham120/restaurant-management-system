package com.restaurant.core.finance.strategy;

import com.restaurant.core.finance.dto.PaymentResponse;
import com.restaurant.core.finance.dto.StartPaymentRequest;

/**
 * Strategy cho một cách thu tiền hóa đơn đang chờ thu (PAYMENT_PENDING).
 * Thêm phương thức mới = thêm một @Component cài đặt interface này (PaymentServiceImpl tự nhận).
 */
public interface PaymentStrategy {

    PaymentMethod method();

    /** Chạy trong transaction của PaymentServiceImpl. */
    PaymentResponse startPayment(long invoiceId, long actorId, StartPaymentRequest request);
}
