package com.restaurant.core.inventory.service;

import java.util.List;

import com.restaurant.core.inventory.dto.request.KitchenStockRequests.AdjustmentRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.AdjustmentResponse;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.MarkUnusableResponse;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.PreparationRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.PreparationResponse;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.ReturnRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.ReturnResponse;

/** Kho bếp: sơ chế, hoàn trả về kho chính, hủy hàng, đánh dấu lô hỏng. Owner: Đỗ Tuấn Anh. */
public interface KitchenStockService {

    // ---------- sơ chế
    PreparationResponse createPreparation(long actorId, PreparationRequest request);

    /** Xác nhận sơ chế: trừ phần chờ sơ chế (WIP), sinh lô bếp PREPARED cho từng đầu ra. */
    PreparationResponse confirmPreparation(long preparationId, long actorId);

    PreparationResponse getPreparationById(long preparationId);

    List<PreparationResponse.Header> getPreparations(String status, int page, int size);

    // ---------- hoàn trả
    ReturnResponse createStockReturn(long actorId, ReturnRequest request);

    /** Xác nhận hoàn trả: trừ ở bếp (WIP hoặc lô bếp, không đụng phần đang giữ cho món), cộng lại lô kho chính. */
    ReturnResponse confirmStockReturn(long returnId, long actorId);

    ReturnResponse getStockReturnById(long returnId);

    List<ReturnResponse.Header> getStockReturns(String status, int page, int size);

    // ---------- hủy hàng
    AdjustmentResponse createStockAdjustment(long actorId, AdjustmentRequest request);

    /** Xác nhận hủy hàng: trừ tồn từng nguồn (không trừ vào phần đang giữ cho món). */
    AdjustmentResponse confirmStockAdjustment(long adjustmentId, long actorId);

    AdjustmentResponse getStockAdjustmentById(long adjustmentId);

    List<AdjustmentResponse.Header> getStockAdjustments(String status, int page, int size);

    // ---------- lô hỏng
    /**
     * Đánh dấu lô không dùng được (RAW: lô kho chính; KITCHEN: lô bếp). Với lô bếp, các món đang giữ nguyên liệu ở lô đó
     * bị gắn cờ phải cấp lại.
     */
    MarkUnusableResponse markBatchUnusable(String kind, long batchId, long actorId, String reason);
}
