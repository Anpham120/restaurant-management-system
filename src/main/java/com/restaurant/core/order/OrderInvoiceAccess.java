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

    /**
     * Khách gọi thêm món khi hóa đơn đã chốt, đang chờ thu: tự mở lại hóa đơn về nháp (thu ngân chốt lại khi tính tiền),
     * để người phục vụ không phải nhờ thu ngân mở lại trước. Không tự mở khi đang có mã QR chờ tiền về (khách có thể
     * đang chuyển khoản): lúc đó báo lỗi, thu ngân hủy mã QR trước. Hóa đơn đã kết thúc (PAID…) cũng báo lỗi.
     * Đơn phải đã được khóa.
     */
    void reopenInvoiceForMoreOrdering(long orderId);

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
