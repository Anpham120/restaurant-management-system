package vn.khoibep.rms.order.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** BR-35: why money was taken off a bill. OTHER needs a note. */
@Getter
@RequiredArgsConstructor
public enum AdjustmentReason {
    WAIT("Chờ lâu"),
    FOOD_QUALITY("Lỗi món"),
    STAFF_ERROR("Lỗi nhân viên"),
    PROMOTION("Khuyến mãi"),
    OTHER("Khác");

    /** How the audit log writes the reason. */
    private final String label;
}
