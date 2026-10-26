package com.restaurant.core.order.dto;

import java.util.List;

/**
 * Kết quả rc_submit_batch (JSONB). warnings là CẢNH BÁO hết suất (QĐ-4) — đợt món vẫn được ghi; Kitchen sẽ kiểm cứng
 * khi nhận món. Không chuyển cảnh báo thành lỗi.
 */
public record SubmitBatchResponse(long batchId, boolean replayed, List<QuotaWarning> warnings, List<OrderItemResponse> items) {

    public record QuotaWarning(String code, long orderItemId, long dailyMenuItemId, String itemName, int quantity,
                               int quotaTotal, long quotaUsed, long remaining) {
    }
}
