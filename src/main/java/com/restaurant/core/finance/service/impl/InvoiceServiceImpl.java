package com.restaurant.core.finance.service.impl;

import java.math.BigDecimal;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.dto.response.InvoiceResponse;
import com.restaurant.core.finance.entity.Invoice;
import com.restaurant.core.finance.repository.InvoiceQueryRepository;
import com.restaurant.core.finance.repository.InvoiceRepository;
import com.restaurant.core.finance.sepay.SepayQr;
import com.restaurant.core.finance.service.InvoiceService;
import com.restaurant.core.finance.service.impl.InvoiceLocking.LockedInvoice;
import com.restaurant.core.order.OrderBillingApi.OrderForBilling;
import com.restaurant.core.order.OrderBillingApi;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceQueryRepository invoiceQueryRepository;
    private final InvoiceLocking invoiceLocking;
    private final InvoiceCalculator invoiceCalculator;
    private final OrderBillingApi orderBillingApi;
    private final SepayQr sepayQr;
    private final BusinessDateProvider clock;

    @Override
    @Transactional
    public InvoiceResponse checkoutOrder(long orderId, long actorId) {
        OrderForBilling order = orderBillingApi.lockOrderForBilling(orderId);
        if (!order.open()) {
            throw ApiException.invalidState("Đơn " + orderId + " không còn mở");
        }
        Invoice existing = invoiceLocking.lockInvoiceOfLockedOrder(orderId).map(LockedInvoice::invoice).orElse(null);
        if (existing != null && !existing.isDraft()) {
            throw ApiException.invalidState("Hóa đơn " + existing.getId() + " đang " + existing.getStatus());
        }
        if (orderBillingApi.hasUnfinishedItems(orderId)) {
            throw ApiException.invalidState("Đơn " + orderId + " còn món chưa phục vụ xong");
        }
        if (orderBillingApi.getServedItems(orderId).isEmpty()) {
            throw ApiException.invalidState("Đơn " + orderId + " không có món phải thu: đóng phiên trống thay vì chốt hóa đơn");
        }
        Invoice invoice = invoiceCalculator.findOrCreateDraftInvoice(existing, orderId, actorId);
        invoiceCalculator.addMissingServedItems(invoice);
        BigDecimal total = invoiceCalculator.calculateVerifiedTotal(invoice);
        if (total.signum() <= 0) {
            throw ApiException.invalidRequest("Hóa đơn " + invoice.getId() + " tổng 0 đồng: không chờ thu, Quản lý dùng miễn thu");
        }
        invoice.lockForPayment(total);
        invoiceRepository.flush();
        return getInvoiceById(invoice.getId());
    }

    @Override
    @Transactional
    public InvoiceResponse reopenInvoice(long invoiceId, long actorId, String reason) {
        String cleanReason = requireReason(reason, "Mở lại hóa đơn bắt buộc có lý do");
        LockedInvoice locked = invoiceLocking.lockInvoiceById(invoiceId);
        if (!locked.invoice().isAwaitingPayment()) {
            throw ApiException.invalidState("Hóa đơn " + invoiceId + " đang " + locked.invoice().getStatus() + ", không phải chờ thanh toán");
        }
        invoiceCalculator.cancelOpenPaymentRequests(locked.openRequests(), cleanReason, actorId);
        locked.invoice().reopen();
        invoiceRepository.flush();
        return getInvoiceById(invoiceId);
    }

    @Override
    @Transactional
    public InvoiceResponse waiveOrderInvoice(long orderId, long actorId, String reason) {
        String cleanReason = requireReason(reason, "Miễn thu bắt buộc có lý do");
        OrderForBilling order = orderBillingApi.lockOrderForBilling(orderId);
        LockedInvoice locked = invoiceLocking.lockInvoiceOfLockedOrder(orderId).orElse(null);
        Invoice existing = locked == null ? null : locked.invoice();
        if (existing != null && !existing.isDraft() && !existing.isAwaitingPayment()) {
            throw ApiException.invalidState("Hóa đơn " + existing.getId() + " đã " + existing.getStatus() + ": không miễn thu được");
        }
        if (!order.open()) {
            throw ApiException.invalidState("Đơn " + orderId + " không còn mở");
        }
        orderBillingApi.lockSessionAndTable(orderId);
        if (orderBillingApi.hasUnfinishedItems(orderId)) {
            throw ApiException.invalidState("Đơn " + orderId + " còn món chưa kết thúc: phục vụ hoặc hủy trước khi miễn thu");
        }
        if (existing == null && orderBillingApi.getServedItems(orderId).isEmpty()) {
            throw ApiException.invalidState("Đơn " + orderId + " không có món phải thu và chưa có hóa đơn: đóng phiên trống");
        }
        Invoice invoice = invoiceCalculator.findOrCreateDraftInvoice(existing, orderId, actorId);
        if (invoice.isDraft()) {
            invoiceCalculator.addMissingServedItems(invoice);
        }
        BigDecimal total = invoiceCalculator.calculateVerifiedTotal(invoice);
        if (invoice.isAwaitingPayment() && total.compareTo(invoice.getTotal()) != 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVOICE_TOTAL_MISMATCH",
                    "Hóa đơn " + invoice.getId() + ": tổng đã chốt khác tổng các dòng");
        }
        if (locked != null) {
            invoiceCalculator.cancelOpenPaymentRequests(locked.openRequests(), "Miễn thu: " + cleanReason, actorId);
        }
        invoice.waive(total, cleanReason, actorId, clock.now());
        invoiceRepository.flush();
        orderBillingApi.closeOrderAndReleaseTable(orderId, actorId);
        return getInvoiceById(invoice.getId());
    }

    @Override
    public InvoiceResponse getInvoiceByOrderId(long orderId) {
        return getInvoiceById(invoiceQueryRepository.findInvoiceIdOfOrder(orderId)
                .orElseThrow(() -> ApiException.notFound("Đơn " + orderId + " chưa có hóa đơn")));
    }

    @Override
    public InvoiceResponse getInvoiceById(long invoiceId) {
        InvoiceResponse.Header header = invoiceQueryRepository.findInvoiceHeader(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hóa đơn " + invoiceId));
        List<InvoiceResponse.PaymentRequestView> requests = invoiceQueryRepository.findPaymentRequests(invoiceId).stream()
                .map(r -> "QR".equals(r.method()) && "WAITING_TRANSFER".equals(r.status())
                        ? r.withQrImageUrl(sepayQr.imageUrl(r.amountDue(), r.paymentReference())) : r)
                .toList();
        return InvoiceResponse.of(header, invoiceQueryRepository.findInvoiceLines(invoiceId), requests,
                invoiceQueryRepository.findPaymentOfInvoice(invoiceId).orElse(null));
    }

    static String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalidRequest(message);
        }
        return reason.strip();
    }
}
