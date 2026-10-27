package com.restaurant.core.inventory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.inventory.dto.KitchenStockDtos.AdjustmentLine;
import com.restaurant.core.inventory.dto.KitchenStockDtos.AdjustmentRequest;
import com.restaurant.core.inventory.dto.KitchenStockDtos.AdjustmentResponse;
import com.restaurant.core.inventory.dto.KitchenStockDtos.MarkUnusableResponse;
import com.restaurant.core.inventory.dto.KitchenStockDtos.OutputView;
import com.restaurant.core.inventory.dto.KitchenStockDtos.PreparationOutput;
import com.restaurant.core.inventory.dto.KitchenStockDtos.PreparationRequest;
import com.restaurant.core.inventory.dto.KitchenStockDtos.PreparationResponse;
import com.restaurant.core.inventory.dto.KitchenStockDtos.ReturnLine;
import com.restaurant.core.inventory.dto.KitchenStockDtos.ReturnRequest;
import com.restaurant.core.inventory.dto.KitchenStockDtos.ReturnResponse;
import com.restaurant.core.inventory.repository.KitchenStockRepository;

/**
 * Sơ chế, hoàn trả, hủy hàng: tạo phiếu nháp (header + dòng, một transaction) rồi xác nhận bằng hàm rc_*. Gửi lặp cùng
 * requestKey → trả phiếu đã có (request_key UNIQUE). Owner: Đỗ Tuấn Anh.
 */
@Service
public class KitchenStockService {

    private final KitchenStockRepository repo;

    public KitchenStockService(KitchenStockRepository repo) {
        this.repo = repo;
    }

    // ---------- sơ chế
    @Transactional
    public PreparationResponse createPreparation(long actorId, PreparationRequest r) {
        Optional<Long> existing = repo.findIdByRequestKey("kitchen_preparations", r.requestKey());
        if (existing.isPresent()) {
            return preparation(existing.get());
        }
        long id = repo.insertPreparation(r.stockIssueItemId(), r.inputQuantity(), blankToNull(r.lossReason()), actorId, r.requestKey());
        for (PreparationOutput o : r.outputs()) {
            repo.insertPreparationOutput(id, o.kitchenIngredientTypeId(), o.outputQuantity(), o.usableUntil());
        }
        return preparation(id);
    }

    public PreparationResponse confirmPreparation(long id, long actorId) {
        repo.confirmPreparation(id, actorId);
        return preparation(id);
    }

    public PreparationResponse preparation(long id) {
        PreparationResponse.Header h = repo.findPreparation(id)
                .orElseThrow(() -> StockDocumentService.notFound("Không tìm thấy phiếu sơ chế " + id));
        List<OutputView> outputs = repo.findPreparationOutputs(id);
        BigDecimal out = outputs.stream().map(OutputView::outputQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PreparationResponse(h.id(), h.status(), h.stockIssueItemId(), h.inputQuantity(), out, h.inputQuantity().subtract(out),
                h.lossReason(), h.preparedById(), h.preparedAt(), h.requestKey(), outputs);
    }

    // ---------- hoàn trả
    @Transactional
    public ReturnResponse createReturn(long actorId, ReturnRequest r) {
        Optional<Long> existing = repo.findIdByRequestKey("stock_returns", r.requestKey());
        if (existing.isPresent()) {
            return stockReturn(existing.get());
        }
        long id = repo.insertReturn(actorId, r.reason().strip(), r.requestKey());
        for (ReturnLine l : r.items()) {
            repo.insertReturnLine(id, l.sourceIssueItemId(), l.directKitchenBatchId(), l.quantity());
        }
        return stockReturn(id);
    }

    public ReturnResponse confirmReturn(long id, long actorId) {
        repo.confirmReturn(id, actorId);
        return stockReturn(id);
    }

    public ReturnResponse stockReturn(long id) {
        ReturnResponse.Header h = repo.findReturn(id)
                .orElseThrow(() -> StockDocumentService.notFound("Không tìm thấy phiếu hoàn trả " + id));
        return new ReturnResponse(h.id(), h.status(), h.reason(), h.requestedById(), h.confirmedById(), h.createdAt(), h.confirmedAt(),
                h.requestKey(), repo.findReturnLines(id));
    }

    // ---------- hủy hàng
    @Transactional
    public AdjustmentResponse createAdjustment(long actorId, AdjustmentRequest r) {
        for (AdjustmentLine l : r.items()) {
            StockDocumentService.requireOneSource(l.rawBatchId(), l.wipIssueItemId(), l.kitchenBatchId());
        }
        Optional<Long> existing = repo.findIdByRequestKey("stock_adjustments", r.requestKey());
        if (existing.isPresent()) {
            return adjustment(existing.get());
        }
        long id = repo.insertAdjustment(actorId, r.adjustmentType(), r.reason().strip(), r.requestKey());
        for (AdjustmentLine l : r.items()) {
            repo.insertAdjustmentLine(id, l.rawBatchId(), l.wipIssueItemId(), l.kitchenBatchId(), l.quantity().negate(),
                    blankToNull(l.note()));
        }
        return adjustment(id);
    }

    public AdjustmentResponse confirmAdjustment(long id, long actorId) {
        repo.confirmAdjustment(id, actorId);
        return adjustment(id);
    }

    public AdjustmentResponse adjustment(long id) {
        AdjustmentResponse.Header h = repo.findAdjustment(id)
                .orElseThrow(() -> StockDocumentService.notFound("Không tìm thấy phiếu hủy hàng " + id));
        return new AdjustmentResponse(h.id(), h.status(), h.adjustmentType(), h.reason(), h.createdById(), h.confirmedById(),
                h.createdAt(), h.confirmedAt(), h.requestKey(), repo.findAdjustmentLines(id));
    }

    // ---------- lô hỏng
    public MarkUnusableResponse markUnusable(String kind, long batchId, long actorId, String reason) {
        return new MarkUnusableResponse(kind, batchId, repo.markUnusable(kind, batchId, actorId, reason.strip()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
