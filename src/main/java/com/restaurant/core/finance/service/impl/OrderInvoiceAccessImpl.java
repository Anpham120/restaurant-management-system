package com.restaurant.core.finance.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.entity.Invoice;
import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.repository.InvoiceItemRepository;
import com.restaurant.core.finance.repository.InvoiceRepository;
import com.restaurant.core.finance.service.impl.InvoiceLocking.LockedInvoice;
import com.restaurant.core.order.OrderInvoiceAccess;

/** Cài đặt cho module Gọi món (interface khai báo ở order). Đơn đã được người gọi khóa trước. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderInvoiceAccessImpl implements OrderInvoiceAccess {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceLocking invoiceLocking;
    private final InvoiceCalculator invoiceCalculator;
    private final BusinessDateProvider clock;

    @Override
    public Optional<String> findInvoiceStatus(long orderId) {
        return invoiceRepository.findByOrderId(orderId).map(i -> i.getStatus().name());
    }

    @Override
    public Optional<String> findInvoiceStatusForUpdate(long orderId) {
        return invoiceLocking.lockInvoiceOfLockedOrder(orderId).map(locked -> locked.invoice().getStatus().name());
    }

    @Override
    public void removeItemFromDraftInvoice(long orderId, long orderItemId) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId).orElseThrow();
        invoiceItemRepository.deleteByInvoiceIdAndOrderItemId(invoice.getId(), orderItemId);
        invoiceItemRepository.flush();
        invoice.updateTotal(invoiceCalculator.sumLines(invoiceItemRepository.findByInvoiceIdOrderById(invoice.getId())));
        invoiceRepository.flush();
    }

    @Override
    public long recordAbandonedOrderInvoice(long orderId, long actorId, String reason) {
        Optional<LockedInvoice> locked = invoiceLocking.lockInvoiceOfLockedOrder(orderId);
        Invoice invoice = invoiceCalculator.findOrCreateDraftInvoice(locked.map(LockedInvoice::invoice).orElse(null), orderId, actorId);
        if (invoice.isDraft()) {
            invoiceCalculator.addMissingServedItems(invoice);
        }
        BigDecimal total = invoiceCalculator.calculateVerifiedTotal(invoice);
        String fullReason = "Khách bỏ về: " + reason;
        List<PaymentRequest> openRequests = locked.map(LockedInvoice::openRequests).orElse(List.of());
        invoiceCalculator.cancelOpenPaymentRequests(openRequests, fullReason, actorId);
        if (total.signum() > 0) {
            invoice.markUncollected(total, reason, actorId, clock.now());          // còn tiền phải thu
        } else {
            invoice.waive(BigDecimal.ZERO, fullReason + " (hóa đơn 0 đồng)", actorId, clock.now());
        }
        invoiceRepository.flush();
        return invoice.getId();
    }
}
