package com.restaurant.core.finance.service.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.finance.entity.Invoice;
import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.entity.PaymentRequestStatus;
import com.restaurant.core.finance.repository.InvoiceRepository;
import com.restaurant.core.finance.repository.PaymentRequestRepository;
import com.restaurant.core.order.OrderBillingApi;

/**
 * Khóa hóa đơn đúng thứ tự chung: đơn → hóa đơn → các yêu cầu thanh toán đang mở. Mọi nghiệp vụ thu tiền đi qua đây,
 * nên thanh toán, mở lại hóa đơn, miễn thu, webhook của cùng một bàn luôn chạy lần lượt.
 */
@Component
@RequiredArgsConstructor
public class InvoiceLocking {

    private static final EnumSet<PaymentRequestStatus> OPEN = EnumSet.of(PaymentRequestStatus.REQUESTED, PaymentRequestStatus.WAITING_TRANSFER);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final OrderBillingApi orderBillingApi;

    /** Hóa đơn đã khóa cùng các yêu cầu thanh toán đang mở của nó. */
    public record LockedInvoice(Invoice invoice, List<PaymentRequest> openRequests) {
    }

    public LockedInvoice lockInvoiceById(long invoiceId) {
        long orderId = invoiceRepository.findOrderIdOfInvoice(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hóa đơn " + invoiceId));
        orderBillingApi.lockOrderForBilling(orderId);
        return lockInvoiceOfLockedOrder(orderId).orElseThrow();
    }

    /** Đơn phải đã được khóa trước (OrderBillingApi.lockOrderForBilling). Rỗng nếu đơn chưa có hóa đơn. */
    public Optional<LockedInvoice> lockInvoiceOfLockedOrder(long orderId) {
        return invoiceRepository.findByOrderIdForUpdate(orderId)
                .map(invoice -> new LockedInvoice(invoice, paymentRequestRepository.findByInvoiceIdAndStatusInForUpdate(invoice.getId(), OPEN)));
    }
}
