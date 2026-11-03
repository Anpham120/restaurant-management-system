package com.restaurant.core.inventory.service;

import java.time.Instant;
import java.util.List;

import com.restaurant.core.inventory.dto.StockDocumentDtos.CountRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.CountResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptResponse;

/**
 * Phiếu nhập, xuất cho bếp, kiểm kê. Mọi thay đổi tồn kho đi kèm dòng sổ kho (stock_movements).
 * Tạo phiếu gửi lặp cùng requestKey → trả phiếu cũ. Owner: Đỗ Tuấn Anh.
 */
public interface StockDocumentService {

    // ---------- phiếu nhập
    ReceiptResponse createStockReceipt(long actorId, ReceiptRequest request);

    /** Xác nhận nhập kho: mỗi dòng thành một lô kho chính. Lô đã hết hạn, nguyên liệu ngừng dùng → từ chối. */
    ReceiptResponse confirmStockReceipt(long receiptId, long actorId, Instant receivedAt);

    void deleteDraftReceipt(long receiptId);

    ReceiptResponse getReceiptById(long receiptId);

    List<ReceiptResponse.Header> getReceipts(String status, int page, int size);

    // ---------- phiếu xuất
    IssueResponse createStockIssue(long actorId, IssueRequest request);

    /** Xuất kho: trừ lô kho chính. Lô phải còn dùng được, đủ số lượng và đúng FEFO (lô hết hạn sớm hơn dùng trước). */
    IssueResponse issueStockToKitchen(long issueId, long actorId);

    /** Bếp nhận đủ cả phiếu: dòng DIRECT thành lô bếp ngay, dòng PREPARE thành phần chờ sơ chế. */
    IssueResponse receiveIssuedStock(long issueId, long actorId);

    /** Hủy phiếu nháp hoặc đã xuất (trả lại kho chính). Bếp đã nhận → dùng phiếu hoàn trả. */
    IssueResponse cancelStockIssue(long issueId, long actorId, String reason);

    IssueResponse getIssueById(long issueId);

    List<IssueResponse.Header> getIssues(String status, int page, int size);

    // ---------- phiếu kiểm kê
    /** Tạo phiếu kiểm kê: chụp số sổ hiện tại của từng nguồn. */
    CountResponse createStockCount(long actorId, CountRequest request);

    /** Xác nhận kiểm kê: số sổ phải chưa đổi từ lúc tạo phiếu; chỉnh tồn theo số thực đếm. */
    CountResponse confirmStockCount(long countId, long actorId);

    CountResponse getCountById(long countId);

    List<CountResponse.Header> getCounts(String status, int page, int size);
}
