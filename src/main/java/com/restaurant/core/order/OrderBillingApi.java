package com.restaurant.core.order;

import java.math.BigDecimal;
import java.util.List;

/**
 * API của module Gọi món cho module Tài chính (hóa đơn, thanh toán). Cài đặt: {@code order.service.impl.OrderBillingApiImpl}.
 * Mọi method chạy trong transaction của người gọi.
 */
public interface OrderBillingApi {

    /** Đơn đã khóa để lập/chốt hóa đơn. */
    record OrderForBilling(long orderId, boolean open, long tableSessionId) {
    }

    /** Một món đã phục vụ, phải thu tiền: tên/giá/số lượng chụp lúc gọi. */
    record ServedItem(long orderItemId, String itemName, int quantity, BigDecimal unitPrice) {
    }

    /** Khóa đơn (bước khóa đầu tiên của mọi nghiệp vụ thu tiền). Không có đơn → 404. */
    OrderForBilling lockOrderForBilling(long orderId);

    /** Đơn còn món chưa kết thúc (chưa phục vụ, chưa hủy, chưa bị từ chối). */
    boolean hasUnfinishedItems(long orderId);

    List<ServedItem> getServedItems(long orderId);

    /** Khóa phiên và bàn của đơn (bước khóa sau hóa đơn và yêu cầu thanh toán). */
    void lockSessionAndTable(long orderId);

    /** Hóa đơn đã kết thúc (đã thu / miễn thu): đóng đơn, đóng phiên, trả bàn. */
    void closeOrderAndReleaseTable(long orderId, long actorId);
}
