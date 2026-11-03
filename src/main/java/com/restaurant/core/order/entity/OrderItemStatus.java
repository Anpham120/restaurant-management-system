package com.restaurant.core.order.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Trạng thái món: PENDING → ACCEPTED → COOKING → READY → SERVED; nhánh REJECTED (bếp từ chối) và CANCELLED (hủy).
 * Bảng chuyển trạng thái hợp lệ nằm ở {@link #canChangeTo}.
 */
public enum OrderItemStatus {
    PENDING, ACCEPTED, COOKING, READY, SERVED, CANCELLED, REJECTED;

    /** Trạng thái "đã chiếm suất" khi tính hạn mức. */
    public static final Set<OrderItemStatus> USING_QUOTA = EnumSet.of(ACCEPTED, COOKING, READY, SERVED);

    /** Trạng thái không còn phải thu tiền. */
    public static final Set<OrderItemStatus> NOT_BILLABLE = EnumSet.of(CANCELLED, REJECTED);

    public boolean canChangeTo(OrderItemStatus next) {
        return switch (this) {
            case PENDING -> next == ACCEPTED || next == REJECTED || next == CANCELLED;
            case ACCEPTED -> next == COOKING || next == CANCELLED;
            case COOKING -> next == READY || next == CANCELLED;
            case READY -> next == SERVED || next == CANCELLED;
            case SERVED -> next == CANCELLED;
            case CANCELLED, REJECTED -> false;
        };
    }
}
