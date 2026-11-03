package com.restaurant.core.finance.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.restaurant.core.finance.entity.Invoice;
import com.restaurant.core.finance.repository.InvoiceRepository;
import com.restaurant.core.order.OrderBillingApi;

/** Hóa đơn đã thu đủ: PAID, rồi đóng đơn, đóng phiên, trả bàn (đúng thứ tự database kiểm). */
@Component
@RequiredArgsConstructor
public class InvoiceSettlement {

    private final InvoiceRepository invoiceRepository;
    private final OrderBillingApi orderBillingApi;

    public void settlePaidInvoice(Invoice invoice, long actorId) {
        invoice.markPaid();
        invoiceRepository.flush();
        orderBillingApi.closeOrderAndReleaseTable(invoice.getOrderId(), actorId);
    }
}
