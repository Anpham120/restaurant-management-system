package com.restaurant.core.finance.strategy;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.finance.dto.PaymentRequest;
import com.restaurant.core.finance.dto.PaymentResponse;
import com.restaurant.core.finance.repository.PaymentRepository;

/** Tiền mặt: thu ngay. rc_pay_cash tạo request PAID + payments và đóng hóa đơn/đơn/phiên/bàn trong một lần gọi. */
@Component
public class CashPaymentStrategy implements PaymentStrategy {

    private final PaymentRepository payments;

    public CashPaymentStrategy(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CASH;
    }

    @Override
    public PaymentResponse start(long invoiceId, long actorId, PaymentRequest request) {
        if (request.cashReceived() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Thanh toán tiền mặt cần cashReceived");
        }
        long paymentId = payments.payCash(invoiceId, actorId, request.cashReceived());
        return new PaymentResponse(invoiceId, PaymentMethod.CASH, "PAID", paymentId, null, payments.findPaymentAmount(paymentId), null);
    }
}
