package com.restaurant.core.finance.strategy;

import com.restaurant.core.finance.dto.PaymentRequest;
import com.restaurant.core.finance.dto.PaymentResponse;

/**
 * Strategy cho cách KHỞI TẠO thanh toán một hóa đơn PAYMENT_PENDING. Mỗi strategy chỉ chọn hàm rc_* phù hợp;
 * toàn bộ ghi tiền (payment_requests, payments, đóng hóa đơn/đơn/phiên/bàn) do PostgreSQL làm nguyên tử.
 * Thêm phương thức mới = thêm một @Component implement interface này (PaymentService tự nhận).
 */
public interface PaymentStrategy {

    PaymentMethod method();

    PaymentResponse start(long invoiceId, long actorId, PaymentRequest request);
}
