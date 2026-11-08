package com.restaurant.core.finance.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.dto.response.PaymentResponse;
import com.restaurant.core.finance.dto.request.StartPaymentRequest;
import com.restaurant.core.finance.entity.Invoice;
import com.restaurant.core.finance.entity.Payment;
import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.repository.PaymentRepository;
import com.restaurant.core.finance.repository.PaymentRequestRepository;
import com.restaurant.core.finance.service.impl.InvoiceLocking.LockedInvoice;
import com.restaurant.core.finance.service.impl.InvoiceLocking;
import com.restaurant.core.finance.service.impl.InvoiceSettlement;

/** Tiền mặt: thu ngay. Ghi lần thu (PAID) + giao dịch, rồi đóng hóa đơn, đơn, phiên, trả bàn. */
@Component
@RequiredArgsConstructor
public class CashPaymentStrategy implements PaymentStrategy {

    private final InvoiceLocking invoiceLocking;
    private final InvoiceSettlement invoiceSettlement;
    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRepository paymentRepository;
    private final BusinessDateProvider clock;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CASH;
    }

    @Override
    public PaymentResponse startPayment(long invoiceId, long actorId, StartPaymentRequest request) {
        if (request.cashReceived() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Thanh toán tiền mặt cần cashReceived");
        }
        LockedInvoice locked = invoiceLocking.lockInvoiceById(invoiceId);
        Invoice invoice = locked.invoice();
        if (!invoice.isAwaitingPayment()) {
            throw ApiException.invalidState("Hóa đơn " + invoiceId + " đang " + invoice.getStatus() + ", không chờ thanh toán");
        }
        if (!locked.openRequests().isEmpty()) {
            throw ApiException.invalidState("Hóa đơn " + invoiceId + " đang có yêu cầu QR mở: hủy trước khi thu tiền mặt");
        }
        if (request.cashReceived().compareTo(invoice.getTotal()) < 0) {
            throw ApiException.invalidRequest("Tiền khách đưa " + request.cashReceived().toPlainString() + " nhỏ hơn số phải thu "
                    + invoice.getTotal().toPlainString());
        }
        var now = clock.now();
        PaymentRequest cashRequest = paymentRequestRepository.save(
                PaymentRequest.paidInCash(invoice, actorId, invoice.getTotal(), request.cashReceived(), now));
        Payment payment = paymentRepository.save(new Payment(invoice, cashRequest, null, invoice.getTotal(), PaymentMethod.CASH,
                now, BusinessDateProvider.businessDateOf(now)));
        invoiceSettlement.settlePaidInvoice(invoice, actorId);
        return new PaymentResponse(invoiceId, PaymentMethod.CASH, "PAID", payment.getId(), null, payment.getAmount(), null);
    }
}
