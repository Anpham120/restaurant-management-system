package com.restaurant.core.inventory.entity;

/** Loại dòng sổ kho (stock_movements.movement_type) và nhóm hiển thị trên báo cáo lịch sử kho. */
public enum MovementType {
    RECEIPT, ISSUE_OUT, ISSUE_REVERSAL, ISSUE_RECEIVED, DIRECT_IN, PREP_INPUT, PREP_OUTPUT, COOK, RETURN, WASTE, COUNT_ADJUST;

    /** Nhóm trên báo cáo: NHẬP, XUẤT, HOÀN TRẢ, TIÊU HAO, HỦY HÀNG, ĐIỀU CHỈNH. */
    public String reportCategory() {
        return switch (this) {
            case RECEIPT -> "NHẬP";
            case ISSUE_OUT, ISSUE_REVERSAL, ISSUE_RECEIVED, DIRECT_IN -> "XUẤT";
            case RETURN -> "HOÀN TRẢ";
            case COOK, PREP_INPUT, PREP_OUTPUT -> "TIÊU HAO";
            case WASTE -> "HỦY HÀNG";
            case COUNT_ADJUST -> "ĐIỀU CHỈNH";
        };
    }
}
