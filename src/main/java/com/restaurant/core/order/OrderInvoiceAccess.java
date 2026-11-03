package com.restaurant.core.order;

import java.util.Optional;

/**
 * Những gì module Gọi món cần từ hóa đơn. Module Tài chính cài đặt interface này (đảo phụ thuộc: Gọi món không phụ
 * thuộc Tài chính, tránh vòng). Cài đặt: {@code finance.service.impl.OrderInvoiceAccessImpl}.
 */
public interface OrderInvoiceAccess {

    /** Trạng thái hóa đơn của đơn (DRAFT, PAYMENT_PENDING, PAID, UNCOLLECTED, WAIVED); rỗng nếu chưa có hóa đơn. */
    Optional<String> findInvoiceStatus(long orderId);

    /** Như {@link #findInvoiceStatus} nhưng khóa hóa đơn và các yêu cầu thanh toán đang mở của nó. */
    Optional<String> findInvoiceStatusForUpdate(long orderId);

    /** Hủy món đã nấu khi hóa đơn còn nháp: bỏ dòng của món khỏi hóa đơn và tính lại tổng. */
    void removeItemFromDraftInvoice(long orderId, long orderItemId);

    /**
     * Khách bỏ về: lập/bổ sung hóa đơn với các món đã phục vụ, hủy yêu cầu thanh toán đang mở, đánh dấu
     * UNCOLLECTED (còn tiền phải thu) hoặc WAIVED (0 đồng).
     *
     * @return id hóa đơn
     */
    long recordAbandonedOrderInvoice(long orderId, long actorId, String reason);
}
