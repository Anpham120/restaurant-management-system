package com.restaurant.core.finance.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.entity.Invoice;
import com.restaurant.core.finance.entity.Payment;
import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.entity.PaymentRequestStatus;
import com.restaurant.core.finance.entity.PaymentWebhookEvent;
import com.restaurant.core.finance.entity.WebhookProcessingResult;
import com.restaurant.core.finance.repository.PaymentRepository;
import com.restaurant.core.finance.repository.PaymentRequestRepository;
import com.restaurant.core.finance.repository.PaymentWebhookEventRepository;
import com.restaurant.core.finance.service.impl.InvoiceLocking.LockedInvoice;
import com.restaurant.core.finance.strategy.PaymentMethod;

/**
 * Ghi nhận một giao dịch chuyển khoản đã được SePay xác thực.
 * Mỗi mã giao dịch chỉ xử lý một lần (gửi lặp trả lại kết quả cũ). Kết quả:
 * PROCESSED (đúng mã tham chiếu, đúng số tiền, hóa đơn còn chờ thu → thu xong, trả bàn),
 * MISMATCH (mã lạ hoặc lệch tiền), AFTER_CANCEL (yêu cầu QR đã hủy / hóa đơn không còn chờ thu).
 * Hai loại sau được lưu để Quản lý đối soát, không ghi nhận tiền.
 */
@Service
@RequiredArgsConstructor
public class QrTransferProcessor {

    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceLocking invoiceLocking;
    private final InvoiceSettlement invoiceSettlement;
    private final BusinessDateProvider clock;

    @Transactional
    public WebhookProcessingResult recordVerifiedTransfer(String transactionCode, String reference, BigDecimal amount, String rawPayload) {
        if (transactionCode == null || transactionCode.isBlank() || reference == null || amount == null || rawPayload == null) {
            throw ApiException.invalidRequest("Webhook thiếu mã giao dịch, mã tham chiếu, số tiền hoặc payload");
        }
        webhookEventRepository.lockTransactionCode(transactionCode);
        Optional<PaymentWebhookEvent> alreadyRecorded = webhookEventRepository.findByProviderTransactionCode(transactionCode);
        if (alreadyRecorded.isPresent()) {
            return alreadyRecorded.get().getProcessingResult();        // webhook gửi lặp: không ghi lần hai
        }

        Invoice invoice = null;
        PaymentRequest paymentRequest = null;
        Optional<Long> invoiceId = paymentRequestRepository.findInvoiceIdByReference(reference);
        if (invoiceId.isPresent()) {
            LockedInvoice locked = invoiceLocking.lockInvoiceById(invoiceId.get());
            invoice = locked.invoice();
            paymentRequest = paymentRequestRepository.findByPaymentReferenceForUpdate(reference).orElseThrow();
        }

        WebhookProcessingResult result = decideResult(invoice, paymentRequest, amount);
        Instant now = clock.now();
        PaymentWebhookEvent event = webhookEventRepository.save(new PaymentWebhookEvent(
                paymentRequest == null ? null : paymentRequest.getId(), transactionCode, reference, amount, result, rawPayload, now));
        if (result == WebhookProcessingResult.PROCESSED) {
            paymentRepository.save(new Payment(invoice.getId(), paymentRequest.getId(), event.getId(), amount, PaymentMethod.QR, now,
                    BusinessDateProvider.businessDateOf(now)));
            paymentRequest.markPaid();
            paymentRequestRepository.flush();
            invoiceSettlement.settlePaidInvoice(invoice, paymentRequest.getCashierId());
        }
        return result;
    }

    private static WebhookProcessingResult decideResult(Invoice invoice, PaymentRequest paymentRequest, BigDecimal amount) {
        if (paymentRequest == null) {
            return WebhookProcessingResult.MISMATCH;
        }
        if (paymentRequest.getStatus() != PaymentRequestStatus.WAITING_TRANSFER || !invoice.isAwaitingPayment()) {
            return WebhookProcessingResult.AFTER_CANCEL;
        }
        if (amount.compareTo(paymentRequest.getAmountDue()) != 0) {
            return WebhookProcessingResult.MISMATCH;
        }
        return WebhookProcessingResult.PROCESSED;
    }
}
