package com.restaurant.core.inventory.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.inventory.dto.StockDocumentDtos.CountLine;
import com.restaurant.core.inventory.dto.StockDocumentDtos.CountRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.CountResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueLine;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptLine;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptLineView;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptRequest;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptResponse;
import com.restaurant.core.inventory.repository.StockDocumentRepository;

/**
 * Phiếu nhập / xuất / kiểm kê. Payload gửi rc_create_* dùng khóa snake_case (docs/04 §10). Mỗi lời gọi rc_* tự là một
 * transaction nguyên tử — không bọc @Transactional. Owner: Đỗ Tuấn Anh.
 */
@Service
public class StockDocumentService {

    private final StockDocumentRepository repo;
    private final JsonMapper json;

    public StockDocumentService(StockDocumentRepository repo, JsonMapper json) {
        this.repo = repo;
        this.json = json;
    }

    // ---------- nhập
    public ReceiptResponse createReceipt(long actorId, ReceiptRequest r) {
        ObjectNode payload = json.createObjectNode();
        putText(payload, "supplier_name", r.supplierName());
        putText(payload, "note", r.note());
        ArrayNode items = payload.putArray("items");
        for (ReceiptLine l : r.items()) {
            ObjectNode i = items.addObject();
            i.put("ingredient_id", l.ingredientId());
            i.put("input_quantity", l.inputQuantity());
            i.put("input_unit", l.inputUnit().strip());
            i.put("conversion_factor", l.conversionFactor());
            i.put("unit_price_per_input_unit", l.unitPricePerInputUnit());
            putText(i, "lot_code", l.lotCode());
            if (l.expiryDate() != null) {
                i.put("expiry_date", l.expiryDate().toString());
            }
        }
        return receipt(repo.createReceipt(actorId, r.requestKey(), json.writeValueAsString(payload)));
    }

    public ReceiptResponse confirmReceipt(long id, long actorId, Instant receivedAt) {
        repo.confirmReceipt(id, actorId, receivedAt);
        return receipt(id);
    }

    public ReceiptResponse receipt(long id) {
        ReceiptResponse.Header h = repo.findReceipt(id).orElseThrow(() -> notFound("Không tìm thấy phiếu nhập " + id));
        List<ReceiptLineView> lines = repo.findReceiptLines(id);
        BigDecimal total = lines.stream().map(ReceiptLineView::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReceiptResponse(h.id(), h.status(), h.supplierName(), h.note(), h.requestKey(), h.createdAt(), h.createdById(),
                h.receivedAt(), h.confirmedById(), h.confirmedAt(), total, lines);
    }

    public List<ReceiptResponse.Header> receipts(String status, int page, int size) {
        return repo.findReceipts(status, page, size);
    }

    /** Hai câu DELETE (dòng rồi header) trong một transaction. */
    @Transactional
    public void deleteDraftReceipt(long id) {
        ReceiptResponse.Header h = repo.findReceipt(id).orElseThrow(() -> notFound("Không tìm thấy phiếu nhập " + id));
        if (!"DRAFT".equals(h.status()) || repo.deleteDraftReceipt(id) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_DRAFT", "Chỉ xóa được phiếu nhập nháp; phiếu " + id + " đang " + h.status());
        }
    }

    // ---------- xuất
    public IssueResponse createIssue(long actorId, IssueRequest r) {
        ObjectNode payload = json.createObjectNode();
        putText(payload, "note", r.note());
        ArrayNode items = payload.putArray("items");
        for (IssueLine l : r.items()) {
            items.addObject().put("inventory_batch_id", l.inventoryBatchId()).put("quantity", l.quantity())
                    .put("fulfillment_mode", l.fulfillmentMode());
        }
        return issue(repo.createIssue(actorId, r.requestKey(), json.writeValueAsString(payload)));
    }

    public IssueResponse issueStock(long id, long actorId) {
        repo.issue(id, actorId);
        return issue(id);
    }

    public IssueResponse receiveIssue(long id, long actorId) {
        repo.receive(id, actorId);
        return issue(id);
    }

    public IssueResponse cancelIssue(long id, long actorId, String reason) {
        repo.cancelIssue(id, actorId, reason.strip());
        return issue(id);
    }

    public IssueResponse issue(long id) {
        IssueResponse.Header h = repo.findIssue(id).orElseThrow(() -> notFound("Không tìm thấy phiếu xuất " + id));
        return new IssueResponse(h.id(), h.status(), h.note(), h.requestKey(), h.createdAt(), h.issuedById(), h.issuedAt(),
                h.receivedById(), h.receivedAt(), h.cancelReason(), h.cancelledAt(), repo.findIssueLines(id));
    }

    public List<IssueResponse.Header> issues(String status, int page, int size) {
        return repo.findIssues(status, page, size);
    }

    // ---------- kiểm kê
    public CountResponse createCount(long actorId, CountRequest r) {
        ObjectNode payload = json.createObjectNode();
        putText(payload, "note", r.note());
        ArrayNode items = payload.putArray("items");
        for (CountLine l : r.items()) {
            requireOneSource(l.rawBatchId(), l.wipIssueItemId(), l.kitchenBatchId());
            ObjectNode i = items.addObject();
            putId(i, "raw_batch_id", l.rawBatchId());
            putId(i, "wip_issue_item_id", l.wipIssueItemId());
            putId(i, "kitchen_batch_id", l.kitchenBatchId());
            i.put("actual_quantity", l.actualQuantity());
            putText(i, "reason", l.reason());
        }
        return count(repo.createCount(actorId, r.requestKey(), json.writeValueAsString(payload)));
    }

    public CountResponse confirmCount(long id, long actorId) {
        repo.confirmCount(id, actorId);
        return count(id);
    }

    public List<CountResponse.Header> counts(String status, int page, int size) {
        return repo.findCounts(status, page, size);
    }

    public CountResponse count(long id) {
        CountResponse.Header h = repo.findCount(id).orElseThrow(() -> notFound("Không tìm thấy phiếu kiểm kê " + id));
        return new CountResponse(h.id(), h.status(), h.note(), h.requestKey(), h.createdAt(), h.createdById(), h.confirmedById(),
                h.confirmedAt(), repo.findCountLines(id));
    }

    static void requireOneSource(Long raw, Long wip, Long kitchen) {
        int n = (raw == null ? 0 : 1) + (wip == null ? 0 : 1) + (kitchen == null ? 0 : 1);
        if (n != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Mỗi dòng cần đúng một nguồn: rawBatchId, wipIssueItemId hoặc kitchenBatchId");
        }
    }

    private static void putText(ObjectNode node, String key, String value) {
        if (value != null && !value.isBlank()) {
            node.put(key, value.strip());
        }
    }

    private static void putId(ObjectNode node, String key, Long value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
