package com.restaurant.core.inventory.service.impl;

import org.springframework.http.HttpStatus;

import com.restaurant.core.common.exception.ApiException;

/** Luật dòng chứng từ kho (kiểm kê, hủy hàng): mỗi dòng chỉ trỏ đúng một nguồn hàng. */
final class StockSourceRules {

    private StockSourceRules() {
    }

    /** Đúng một trong ba: lô kho chính, dòng WIP ở bếp, lô bếp. */
    static void requireOneSource(Long raw, Long wip, Long kitchen) {
        int n = (raw == null ? 0 : 1) + (wip == null ? 0 : 1) + (kitchen == null ? 0 : 1);
        if (n != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Mỗi dòng cần đúng một nguồn: rawBatchId, wipIssueItemId hoặc kitchenBatchId");
        }
    }
}
