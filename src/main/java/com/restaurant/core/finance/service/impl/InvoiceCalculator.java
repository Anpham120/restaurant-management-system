package com.restaurant.core.finance.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.entity.Invoice;
import com.restaurant.core.finance.entity.InvoiceItem;
import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.repository.InvoiceItemRepository;
import com.restaurant.core.finance.repository.InvoiceRepository;
import com.restaurant.core.order.OrderBillingApi.ServedItem;
import com.restaurant.core.order.OrderBillingApi;

/** Tính tiền hóa đơn: lập dòng từ các món đã phục vụ, kiểm dòng khớp đơn hàng, tính tổng. */
@Component
@RequiredArgsConstructor
public class InvoiceCalculator {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final OrderBillingApi orderBillingApi;
    private final BusinessDateProvider clock;

    /** Hóa đơn của đơn (đã khóa) hoặc tạo hóa đơn nháp mới. */
    public Invoice findOrCreateDraftInvoice(Invoice existing, long orderId, long actorId) {
        return existing != null ? existing : invoiceRepository.save(new Invoice(orderId, actorId, clock.now()));
    }

    /** Thêm vào hóa đơn nháp các món đã phục vụ chưa có dòng. */
    public void addMissingServedItems(Invoice invoice) {
        for (ServedItem served : orderBillingApi.getServedItems(invoice.getOrderId())) {
            if (!invoiceItemRepository.existsByOrderItemId(served.orderItemId())) {
                invoiceItemRepository.save(new InvoiceItem(invoice, served.orderItemId(), served.itemName(), served.quantity(),
                        served.unitPrice()));
            }
        }
    }

    /**
     * Tổng tiền sau khi kiểm hóa đơn khớp đúng đơn hàng: mỗi món đã phục vụ có đúng một dòng, mỗi dòng là bản chụp
     * đúng tên/số lượng/giá của món. Lệch → 409 (không thu tiền sai).
     */
    public BigDecimal calculateVerifiedTotal(Invoice invoice) {
        Map<Long, ServedItem> served = orderBillingApi.getServedItems(invoice.getOrderId()).stream()
                .collect(Collectors.toMap(ServedItem::orderItemId, Function.identity()));
        List<InvoiceItem> lines = invoiceItemRepository.findByInvoice_IdOrderById(invoice.getId());
        long invalidLines = lines.stream().filter(line -> !matchesServedItem(line, served.get(line.getOrderItemId()))).count();
        long missingLines = served.keySet().stream()
                .filter(itemId -> lines.stream().noneMatch(line -> line.getOrderItemId().equals(itemId))).count();
        if (missingLines > 0 || invalidLines > 0) {
            throw ApiException.invalidState("Hóa đơn " + invoice.getId() + " không khớp đơn hàng: thiếu " + missingLines
                    + " món đã phục vụ, " + invalidLines + " dòng không hợp lệ");
        }
        return sumLines(lines);
    }

    public BigDecimal sumLines(List<InvoiceItem> lines) {
        return lines.stream().map(InvoiceItem::calculateLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Hủy mọi yêu cầu thanh toán đang mở (đã khóa) của hóa đơn. */
    public void cancelOpenPaymentRequests(List<PaymentRequest> openRequests, String reason, long actorId) {
        Instant now = clock.now();
        openRequests.stream().filter(PaymentRequest::isOpen).forEach(r -> r.cancel(reason, actorId, now));
    }

    private static boolean matchesServedItem(InvoiceItem line, ServedItem item) {
        return item != null && line.getItemName().equals(item.itemName()) && line.getQuantity() == item.quantity()
                && line.getUnitPrice().compareTo(item.unitPrice()) == 0;
    }
}
