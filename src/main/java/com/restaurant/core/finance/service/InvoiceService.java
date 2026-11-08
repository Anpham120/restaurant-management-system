package com.restaurant.core.finance.service;

import com.restaurant.core.finance.dto.response.InvoiceResponse;

/** Hóa đơn: chốt, mở lại, miễn thu, xem. Tổng tiền luôn tính từ món đã phục vụ, không nhận từ client. Owner: Phạm Duy An. */
public interface InvoiceService {

    /** Chốt hóa đơn: lập dòng từ các món đã phục vụ, khóa tổng tiền, chờ thu (PAYMENT_PENDING). */
    InvoiceResponse checkoutOrder(long orderId, long actorId);

    /** Mở lại hóa đơn đang chờ thu (để gọi thêm / hủy món): hủy các yêu cầu thanh toán đang mở. */
    InvoiceResponse reopenInvoice(long invoiceId, long actorId, String reason);

    /** Quản lý miễn thu toàn bộ đơn: hóa đơn WAIVED, đóng đơn, trả bàn. */
    InvoiceResponse waiveOrderInvoice(long orderId, long actorId, String reason);

    InvoiceResponse getInvoiceByOrderId(long orderId);

    InvoiceResponse getInvoiceById(long invoiceId);
}
