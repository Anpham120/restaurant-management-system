package com.restaurant.core.finance.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.dto.response.PaymentResponse;
import com.restaurant.core.finance.dto.request.StartPaymentRequest;
import com.restaurant.core.finance.entity.Invoice;
import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.repository.PaymentRequestRepository;
import com.restaurant.core.finance.sepay.SepayQr;
import com.restaurant.core.finance.service.impl.InvoiceLocking.LockedInvoice;
import com.restaurant.core.finance.service.impl.InvoiceLocking;

/**
 * QR/SePay: chỉ tạo yêu cầu chờ chuyển khoản (WAITING_TRANSFER) và trả mã tham chiếu + ảnh QR.
 * KHÔNG ghi nhận đã thu ở đây — hóa đơn chỉ PAID khi webhook SePay báo tiền về (QrTransferProcessor).
 */
@Component
@RequiredArgsConstructor
public class QrPaymentStrategy implements PaymentStrategy {

    private final InvoiceLocking invoiceLocking;
    private final PaymentRequestRepository paymentRequestRepository;
    private final SepayQr sepayQr;
    private final BusinessDateProvider clock;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.QR;
    }

    @Override
    public PaymentResponse startPayment(long invoiceId, long actorId, StartPaymentRequest request) {
        LockedInvoice locked = invoiceLocking.lockInvoiceById(invoiceId);
        Invoice invoice = locked.invoice();
        if (!invoice.isAwaitingPayment()) {
            throw ApiException.invalidState("Hóa đơn " + invoiceId + " đang " + invoice.getStatus() + ", không chờ thanh toán");
        }
        if (!locked.openRequests().isEmpty()) {
            throw ApiException.invalidState("Hóa đơn " + invoiceId + " đã có yêu cầu thanh toán đang mở");
        }
        if (invoice.getTotal().stripTrailingZeros().scale() > 0) {
            // Giá nhập vào đã bắt buộc là số nguyên đồng; chặn thêm ở đây để không bao giờ tạo QR mà webhook không khớp được
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "AMOUNT_NOT_WHOLE_VND",
                    "Tổng hóa đơn " + invoice.getTotal().toPlainString() + " có phần lẻ: không tạo được mã QR, hãy thu tiền mặt");
        }
        String reference = buildTransferReference(invoiceId);
        paymentRequestRepository.save(PaymentRequest.waitingForTransfer(invoice, actorId, invoice.getTotal(), reference, clock.now()));
        return new PaymentResponse(invoiceId, PaymentMethod.QR, "WAITING_TRANSFER", null, reference, invoice.getTotal(),
                sepayQr.imageUrl(invoice.getTotal(), reference));
    }

    /** Mã đưa vào nội dung chuyển khoản, chỉ chữ và số: RC&lt;hóa đơn&gt;Q&lt;lần thứ n&gt;. */
    private String buildTransferReference(long invoiceId) {
        return "RC" + invoiceId + "Q" + (1 + paymentRequestRepository.countByInvoice_Id(invoiceId));
    }
}
