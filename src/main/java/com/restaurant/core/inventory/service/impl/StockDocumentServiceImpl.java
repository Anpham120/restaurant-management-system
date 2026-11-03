package com.restaurant.core.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
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
import com.restaurant.core.inventory.entity.DocumentStatus;
import com.restaurant.core.inventory.entity.FulfillmentMode;
import com.restaurant.core.inventory.entity.Ingredient;
import com.restaurant.core.inventory.entity.InventoryBatch;
import com.restaurant.core.inventory.entity.IssueStatus;
import com.restaurant.core.inventory.entity.KitchenBatch;
import com.restaurant.core.inventory.entity.KitchenIngredientType;
import com.restaurant.core.inventory.entity.MovementType;
import com.restaurant.core.inventory.entity.StockCount;
import com.restaurant.core.inventory.entity.StockCountItem;
import com.restaurant.core.inventory.entity.StockIssue;
import com.restaurant.core.inventory.entity.StockIssueItem;
import com.restaurant.core.inventory.entity.StockMovement;
import com.restaurant.core.inventory.entity.StockReceipt;
import com.restaurant.core.inventory.entity.StockReceiptItem;
import com.restaurant.core.inventory.repository.InventoryBatchRepository;
import com.restaurant.core.inventory.repository.KitchenBatchRepository;
import com.restaurant.core.inventory.repository.KitchenIngredientTypeRepository;
import com.restaurant.core.inventory.repository.IngredientRepository;
import com.restaurant.core.inventory.repository.StockCountItemRepository;
import com.restaurant.core.inventory.repository.StockCountRepository;
import com.restaurant.core.inventory.repository.StockDocumentRepository;
import com.restaurant.core.inventory.repository.StockIssueItemRepository;
import com.restaurant.core.inventory.repository.StockIssueRepository;
import com.restaurant.core.inventory.repository.StockMovementRepository;
import com.restaurant.core.inventory.repository.StockReceiptItemRepository;
import com.restaurant.core.inventory.repository.StockReceiptRepository;
import com.restaurant.core.inventory.service.StockDocumentService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockDocumentServiceImpl implements StockDocumentService {

    private final StockReceiptRepository receiptRepository;
    private final StockReceiptItemRepository receiptItemRepository;
    private final StockIssueRepository issueRepository;
    private final StockIssueItemRepository issueItemRepository;
    private final StockCountRepository countRepository;
    private final StockCountItemRepository countItemRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final KitchenBatchRepository kitchenBatchRepository;
    private final KitchenIngredientTypeRepository kitchenTypeRepository;
    private final IngredientRepository ingredientRepository;
    private final StockMovementRepository movementRepository;
    private final StockDocumentRepository documentQueryRepository;
    private final DocumentRequestKeys requestKeys;
    private final BusinessDateProvider clock;

    // ==================== phiếu nhập

    @Override
    @Transactional
    public ReceiptResponse createStockReceipt(long actorId, ReceiptRequest request) {
        String fingerprint = requestKeys.fingerprintOf(request);
        requestKeys.lockRequestKey("stock_receipts", request.requestKey());
        var existing = receiptRepository.findByRequestKey(request.requestKey());
        if (existing.isPresent()) {
            requestKeys.requireSameRequest(existing.get().getRequestHash(), fingerprint, request.requestKey(), "phiếu nhập");
            return getReceiptById(existing.get().getId());
        }
        StockReceipt receipt = receiptRepository.save(new StockReceipt(blankToNull(request.supplierName()), blankToNull(request.note()),
                actorId, request.requestKey(), fingerprint, clock.now()));
        for (ReceiptLine line : request.items()) {
            receiptItemRepository.save(new StockReceiptItem(receipt.getId(), line.ingredientId(), line.inputQuantity(), line.inputUnit().strip(),
                    line.conversionFactor(), line.unitPricePerInputUnit(), blankToNull(line.lotCode()), line.expiryDate()));
        }
        receiptItemRepository.flush();
        return getReceiptById(receipt.getId());
    }

    @Override
    @Transactional
    public ReceiptResponse confirmStockReceipt(long receiptId, long actorId, Instant receivedAt) {
        StockReceipt receipt = receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập " + receiptId));
        if (receipt.getStatus() != DocumentStatus.DRAFT) {
            throw ApiException.invalidState("Phiếu nhập " + receiptId + " đang " + receipt.getStatus() + ", không phải nháp");
        }
        List<StockReceiptItem> lines = receiptItemRepository.findByStockReceiptIdOrderById(receiptId);
        if (lines.isEmpty()) {
            throw ApiException.invalidRequest("Phiếu nhập " + receiptId + " rỗng");
        }
        Map<Long, Ingredient> ingredients = ingredientRepository.findAllById(lines.stream().map(StockReceiptItem::getIngredientId).toList())
                .stream().collect(Collectors.toMap(Ingredient::getId, Function.identity()));
        LocalDate today = clock.today();
        for (StockReceiptItem line : lines) {
            Ingredient ingredient = ingredients.get(line.getIngredientId());
            if (!ingredient.isActive()) {
                throw ApiException.invalidRequest("Phiếu nhập " + receiptId + " có nguyên liệu đã ngừng sử dụng: " + ingredient.getName());
            }
            if (ingredient.isExpiryTracked() && line.getExpiryDate() == null) {
                throw ApiException.invalidRequest("Phiếu nhập " + receiptId + ": " + ingredient.getName() + " theo dõi hạn dùng, phải có ngày hết hạn");
            }
            if (Ingredient.isExpired(line.getExpiryDate(), today)) {
                throw ApiException.invalidRequest("Phiếu nhập " + receiptId + " có lô đã hết hạn: không nhận vào kho");
            }
        }
        Instant now = clock.now();
        for (StockReceiptItem line : lines) {
            BigDecimal quantity = line.calculateBaseQuantity();
            InventoryBatch batch = inventoryBatchRepository.save(new InventoryBatch(line.getId(), quantity, now));
            movementRepository.save(StockMovement.ofRawBatch(MovementType.RECEIPT, batch.getId(), quantity, "RECEIPT:" + line.getId(), now));
        }
        receipt.confirm(receivedAt == null ? now : receivedAt, actorId, now);   // ghi sổ trước, đổi trạng thái sau
        receiptRepository.flush();
        return getReceiptById(receiptId);
    }

    @Override
    @Transactional
    public void deleteDraftReceipt(long receiptId) {
        StockReceipt receipt = receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập " + receiptId));
        if (receipt.getStatus() != DocumentStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_DRAFT", "Chỉ xóa được phiếu nhập nháp; phiếu " + receiptId + " đang " + receipt.getStatus());
        }
        receiptItemRepository.deleteAll(receiptItemRepository.findByStockReceiptIdOrderById(receiptId));
        receiptItemRepository.flush();
        receiptRepository.delete(receipt);
        receiptRepository.flush();
    }

    @Override
    public ReceiptResponse getReceiptById(long receiptId) {
        ReceiptResponse.Header h = documentQueryRepository.findReceipt(receiptId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập " + receiptId));
        List<ReceiptLineView> lines = documentQueryRepository.findReceiptLines(receiptId);
        BigDecimal total = lines.stream().map(ReceiptLineView::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReceiptResponse(h.id(), h.status(), h.supplierName(), h.note(), h.requestKey(), h.createdAt(), h.createdById(),
                h.receivedAt(), h.confirmedById(), h.confirmedAt(), total, lines);
    }

    @Override
    public List<ReceiptResponse.Header> getReceipts(String status, int page, int size) {
        return documentQueryRepository.findReceipts(status, page, size);
    }

    // ==================== phiếu xuất

    @Override
    @Transactional
    public IssueResponse createStockIssue(long actorId, IssueRequest request) {
        String fingerprint = requestKeys.fingerprintOf(request);
        requestKeys.lockRequestKey("stock_issues", request.requestKey());
        var existing = issueRepository.findByRequestKey(request.requestKey());
        if (existing.isPresent()) {
            requestKeys.requireSameRequest(existing.get().getRequestHash(), fingerprint, request.requestKey(), "phiếu xuất");
            return getIssueById(existing.get().getId());
        }
        StockIssue issue = issueRepository.save(new StockIssue(actorId, blankToNull(request.note()), request.requestKey(), fingerprint, clock.now()));
        for (IssueLine line : request.items()) {
            issueItemRepository.save(new StockIssueItem(issue.getId(), line.inventoryBatchId(), line.quantity(),
                    FulfillmentMode.valueOf(line.fulfillmentMode())));
        }
        issueItemRepository.flush();
        return getIssueById(issue.getId());
    }

    @Override
    @Transactional
    public IssueResponse issueStockToKitchen(long issueId, long actorId) {
        StockIssue issue = findIssueForUpdate(issueId);
        if (issue.getStatus() != IssueStatus.DRAFT) {
            throw ApiException.invalidState("Phiếu xuất " + issueId + " đang " + issue.getStatus() + ", không phải nháp");
        }
        List<StockIssueItem> lines = issueItemRepository.findByStockIssueIdOrderById(issueId);
        if (lines.isEmpty()) {
            throw ApiException.invalidRequest("Phiếu xuất " + issueId + " rỗng");
        }
        List<Long> usedBatchIds = lines.stream().map(StockIssueItem::getInventoryBatchId).distinct().toList();
        List<Long> ingredientIds = receiptItemRepository.findIngredientIdsOfBatches(usedBatchIds);
        List<InventoryBatch> lockedBatches = inventoryBatchRepository.lockBatchesOfIngredients(ingredientIds);
        Map<Long, InventoryBatch> batchById = lockedBatches.stream().collect(Collectors.toMap(InventoryBatch::getId, Function.identity()));
        Map<Long, StockReceiptItem> receiptLineOfBatch = new HashMap<>();
        receiptItemRepository.findAllById(lockedBatches.stream().map(InventoryBatch::getStockReceiptItemId).toList())
                .forEach(r -> lockedBatches.stream().filter(b -> b.getStockReceiptItemId().equals(r.getId()))
                        .forEach(b -> receiptLineOfBatch.put(b.getId(), r)));

        requireDirectLinesHaveKitchenType(issueId, lines, receiptLineOfBatch);
        Map<Long, BigDecimal> requestedByBatch = lines.stream().collect(Collectors.groupingBy(StockIssueItem::getInventoryBatchId,
                Collectors.reducing(BigDecimal.ZERO, StockIssueItem::getQuantity, BigDecimal::add)));
        LocalDate today = clock.today();
        for (Map.Entry<Long, BigDecimal> used : requestedByBatch.entrySet()) {
            InventoryBatch batch = batchById.get(used.getKey());
            if (!batch.isUsable() || Ingredient.isExpired(receiptLineOfBatch.get(batch.getId()).getExpiryDate(), today)) {
                throw ApiException.invalidState("Lô kho " + batch.getId() + " không dùng được hoặc đã hết hạn");
            }
            if (used.getValue().compareTo(batch.getQuantityOnHand()) > 0) {
                throw ApiException.invalidState("Lô kho " + batch.getId() + " chỉ còn " + plain(batch.getQuantityOnHand())
                        + ", phiếu cần " + plain(used.getValue()));
            }
        }
        requireFirstExpiredFirstOut(issueId, requestedByBatch, lockedBatches, receiptLineOfBatch, today);

        Instant now = clock.now();
        for (StockIssueItem line : lines) {
            batchById.get(line.getInventoryBatchId()).changeOnHand(line.getQuantity().negate());
            movementRepository.save(StockMovement.ofRawBatch(MovementType.ISSUE_OUT, line.getInventoryBatchId(), line.getQuantity().negate(),
                    "ISSUE_OUT:" + line.getId(), now).refIssueItem(line.getId()));
        }
        inventoryBatchRepository.flush();
        issue.markIssued(actorId, now);
        issueRepository.flush();
        return getIssueById(issueId);
    }

    @Override
    @Transactional
    public IssueResponse receiveIssuedStock(long issueId, long actorId) {
        StockIssue issue = findIssueForUpdate(issueId);
        if (issue.getStatus() != IssueStatus.ISSUED) {
            throw ApiException.invalidState("Phiếu xuất " + issueId + " đang " + issue.getStatus() + ", chưa xuất kho (bếp nhận một lần, đủ cả phiếu)");
        }
        Instant now = clock.now();
        for (StockIssueItem line : issueItemRepository.findByStockIssueIdOrderById(issueId)) {
            if (line.getFulfillmentMode() == FulfillmentMode.PREPARE) {
                line.setWipRemaining(line.getQuantity());
                movementRepository.save(StockMovement.ofWipIssueItem(MovementType.ISSUE_RECEIVED, line.getId(), line.getQuantity(),
                        "ISSUE_RECEIVED:" + line.getId(), now).refIssueItem(line.getId()));
            } else {
                StockReceiptItem source = receiptLineOf(line.getInventoryBatchId());
                KitchenIngredientType type = kitchenTypeRepository.findActiveDirectTypeOfIngredient(source.getIngredientId())
                        .orElseThrow(() -> ApiException.invalidRequest("Nguyên liệu " + source.getIngredientId() + " chưa khai báo loại bếp dùng thẳng"));
                KitchenBatch kitchenBatch = kitchenBatchRepository.save(KitchenBatch.fromDirectIssue(type.getId(), line.getId(),
                        ShelfLifeCalculator.calculateUsableUntil(source.getExpiryDate(), type.getShelfLifeHours(), now), line.getQuantity(), now));
                movementRepository.save(StockMovement.ofKitchenBatch(MovementType.DIRECT_IN, kitchenBatch.getId(), line.getQuantity(),
                        "DIRECT_IN:" + line.getId(), now).refIssueItem(line.getId()));
            }
        }
        issueItemRepository.flush();
        issue.markReceived(actorId, now);
        issueRepository.flush();
        return getIssueById(issueId);
    }

    @Override
    @Transactional
    public IssueResponse cancelStockIssue(long issueId, long actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalidRequest("Hủy phiếu xuất bắt buộc có lý do");
        }
        StockIssue issue = findIssueForUpdate(issueId);
        if (issue.getStatus() != IssueStatus.DRAFT && issue.getStatus() != IssueStatus.ISSUED) {
            throw ApiException.invalidState("Phiếu xuất " + issueId + " đang " + issue.getStatus() + ": không hủy được (bếp đã nhận → dùng phiếu hoàn trả)");
        }
        Instant now = clock.now();
        if (issue.getStatus() == IssueStatus.ISSUED) {
            List<StockIssueItem> lines = issueItemRepository.findByStockIssueIdOrderById(issueId);
            Map<Long, InventoryBatch> batches = inventoryBatchRepository.lockBatchesByIds(
                    lines.stream().map(StockIssueItem::getInventoryBatchId).distinct().toList())
                    .stream().collect(Collectors.toMap(InventoryBatch::getId, Function.identity()));
            for (StockIssueItem line : lines) {
                batches.get(line.getInventoryBatchId()).changeOnHand(line.getQuantity());
                movementRepository.save(StockMovement.ofRawBatch(MovementType.ISSUE_REVERSAL, line.getInventoryBatchId(), line.getQuantity(),
                        "ISSUE_REVERSAL:" + line.getId(), now).refIssueItem(line.getId()));
            }
            inventoryBatchRepository.flush();
        }
        issue.cancel(reason.strip(), actorId, now);
        issueRepository.flush();
        return getIssueById(issueId);
    }

    @Override
    public IssueResponse getIssueById(long issueId) {
        IssueResponse.Header h = documentQueryRepository.findIssue(issueId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu xuất " + issueId));
        return new IssueResponse(h.id(), h.status(), h.note(), h.requestKey(), h.createdAt(), h.issuedById(), h.issuedAt(),
                h.receivedById(), h.receivedAt(), h.cancelReason(), h.cancelledAt(), documentQueryRepository.findIssueLines(issueId));
    }

    @Override
    public List<IssueResponse.Header> getIssues(String status, int page, int size) {
        return documentQueryRepository.findIssues(status, page, size);
    }

    // ==================== phiếu kiểm kê

    @Override
    @Transactional
    public CountResponse createStockCount(long actorId, CountRequest request) {
        request.items().forEach(l -> requireOneSource(l.rawBatchId(), l.wipIssueItemId(), l.kitchenBatchId()));
        String fingerprint = requestKeys.fingerprintOf(request);
        requestKeys.lockRequestKey("stock_counts", request.requestKey());
        var existing = countRepository.findByRequestKey(request.requestKey());
        if (existing.isPresent()) {
            requestKeys.requireSameRequest(existing.get().getRequestHash(), fingerprint, request.requestKey(), "phiếu kiểm kê");
            return getCountById(existing.get().getId());
        }
        StockCount count = countRepository.save(new StockCount(blankToNull(request.note()), actorId, request.requestKey(), fingerprint, clock.now()));
        for (CountLine line : request.items()) {
            countItemRepository.save(new StockCountItem(count.getId(), line.rawBatchId(), line.wipIssueItemId(), line.kitchenBatchId(),
                    findBookQuantity(line), line.actualQuantity(), blankToNull(line.reason())));
        }
        countItemRepository.flush();
        return getCountById(count.getId());
    }

    @Override
    @Transactional
    public CountResponse confirmStockCount(long countId, long actorId) {
        StockCount count = countRepository.findByIdForUpdate(countId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu kiểm kê " + countId));
        if (count.getStatus() != DocumentStatus.DRAFT) {
            throw ApiException.invalidState("Phiếu kiểm kê " + countId + " đang " + count.getStatus() + ", không phải nháp");
        }
        List<StockCountItem> lines = countItemRepository.findByStockCountIdOrderById(countId);
        if (lines.isEmpty()) {
            throw ApiException.invalidRequest("Phiếu kiểm kê " + countId + " rỗng");
        }
        // khóa đúng thứ tự chung: lô kho chính → dòng xuất (WIP) → lô bếp
        Map<Long, InventoryBatch> rawBatches = idMap(inventoryBatchRepository.lockBatchesByIds(idsOf(lines, StockCountItem::getRawBatchId)),
                InventoryBatch::getId);
        Map<Long, StockIssueItem> wipLines = idMap(issueItemRepository.lockIssueItemsByIds(idsOf(lines, StockCountItem::getWipIssueItemId)),
                StockIssueItem::getId);
        Map<Long, KitchenBatch> kitchenBatches = idMap(kitchenBatchRepository.lockBatchesByIds(idsOf(lines, StockCountItem::getKitchenBatchId)),
                KitchenBatch::getId);
        Instant now = clock.now();
        for (StockCountItem line : lines) {
            BigDecimal current;
            BigDecimal reserved = BigDecimal.ZERO;
            if (line.getRawBatchId() != null) {
                current = rawBatches.get(line.getRawBatchId()).getQuantityOnHand();
            } else if (line.getWipIssueItemId() != null) {
                current = wipLines.get(line.getWipIssueItemId()).getWipRemainingQuantity();
            } else {
                KitchenBatch kb = kitchenBatches.get(line.getKitchenBatchId());
                current = kb.getQuantityOnHand();
                reserved = kb.getReservedQuantity();
            }
            if (current.compareTo(line.getBookQuantity()) != 0) {
                throw ApiException.invalidState("Dòng kiểm kê " + line.getId() + ": số sổ đã đổi từ " + plain(line.getBookQuantity())
                        + " thành " + plain(current) + " — lập lại phiếu");
            }
            if (line.getActualQuantity().compareTo(reserved) < 0) {
                throw ApiException.invalidState("Dòng kiểm kê " + line.getId() + ": thực đếm " + plain(line.getActualQuantity())
                        + " nhỏ hơn lượng đang giữ cho món " + plain(reserved));
            }
            BigDecimal difference = line.calculateDifference();
            if (difference.signum() == 0) {
                continue;
            }
            StockMovement movement;
            if (line.getRawBatchId() != null) {
                rawBatches.get(line.getRawBatchId()).setOnHand(line.getActualQuantity());
                movement = StockMovement.ofRawBatch(MovementType.COUNT_ADJUST, line.getRawBatchId(), difference, "COUNT:" + line.getId(), now);
            } else if (line.getWipIssueItemId() != null) {
                wipLines.get(line.getWipIssueItemId()).setWipRemaining(line.getActualQuantity());
                movement = StockMovement.ofWipIssueItem(MovementType.COUNT_ADJUST, line.getWipIssueItemId(), difference, "COUNT:" + line.getId(), now);
            } else {
                KitchenBatch kb = kitchenBatches.get(line.getKitchenBatchId());
                kb.changeOnHand(line.getActualQuantity().subtract(kb.getQuantityOnHand()));
                movement = StockMovement.ofKitchenBatch(MovementType.COUNT_ADJUST, line.getKitchenBatchId(), difference, "COUNT:" + line.getId(), now);
            }
            movementRepository.save(movement.countItem(line.getId()).reason(line.getReason()));
        }
        inventoryBatchRepository.flush();
        count.confirm(actorId, now);
        countRepository.flush();
        return getCountById(countId);
    }

    @Override
    public CountResponse getCountById(long countId) {
        CountResponse.Header h = documentQueryRepository.findCount(countId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu kiểm kê " + countId));
        return new CountResponse(h.id(), h.status(), h.note(), h.requestKey(), h.createdAt(), h.createdById(), h.confirmedById(),
                h.confirmedAt(), documentQueryRepository.findCountLines(countId));
    }

    @Override
    public List<CountResponse.Header> getCounts(String status, int page, int size) {
        return documentQueryRepository.findCounts(status, page, size);
    }

    // ==================== quy tắc

    /** Dòng DIRECT cần nguyên liệu gốc có loại bếp dùng thẳng đang hoạt động (báo sớm thay vì lỗi lúc bếp nhận). */
    private void requireDirectLinesHaveKitchenType(long issueId, List<StockIssueItem> lines, Map<Long, StockReceiptItem> receiptLineOfBatch) {
        for (StockIssueItem line : lines) {
            if (line.getFulfillmentMode() == FulfillmentMode.DIRECT && kitchenTypeRepository
                    .findActiveDirectTypeOfIngredient(receiptLineOfBatch.get(line.getInventoryBatchId()).getIngredientId()).isEmpty()) {
                throw ApiException.invalidRequest("Phiếu xuất " + issueId + " có dòng dùng thẳng nhưng nguyên liệu chưa khai báo loại bếp dùng thẳng");
            }
        }
    }

    /**
     * FEFO (hết hạn trước, xuất trước): với mỗi lô được xuất, không còn lô cùng nguyên liệu nào dùng được mà hạn sớm hơn
     * (cùng hạn thì nhận kho trước) vẫn còn hàng sau phiếu này.
     */
    private void requireFirstExpiredFirstOut(long issueId, Map<Long, BigDecimal> requestedByBatch, List<InventoryBatch> lockedBatches,
                                             Map<Long, StockReceiptItem> receiptLineOfBatch, LocalDate today) {
        for (Long usedBatchId : requestedByBatch.keySet()) {
            InventoryBatch used = lockedBatches.stream().filter(b -> b.getId().equals(usedBatchId)).findFirst().orElseThrow();
            StockReceiptItem usedLine = receiptLineOfBatch.get(usedBatchId);
            for (InventoryBatch other : lockedBatches) {
                StockReceiptItem otherLine = receiptLineOfBatch.get(other.getId());
                if (other.getId().equals(usedBatchId) || !other.isUsable() || !otherLine.getIngredientId().equals(usedLine.getIngredientId())
                        || Ingredient.isExpired(otherLine.getExpiryDate(), today)) {
                    continue;
                }
                BigDecimal leftAfterIssue = other.getQuantityOnHand().subtract(requestedByBatch.getOrDefault(other.getId(), BigDecimal.ZERO));
                if (leftAfterIssue.signum() > 0 && expiresEarlier(other, otherLine, used, usedLine)) {
                    throw ApiException.invalidState("Phiếu xuất " + issueId + " không theo FEFO: lô " + other.getId()
                            + " hạn sớm hơn lô " + usedBatchId + " chưa dùng hết");
                }
            }
        }
    }

    private static boolean expiresEarlier(InventoryBatch a, StockReceiptItem aLine, InventoryBatch b, StockReceiptItem bLine) {
        LocalDate aExpiry = aLine.getExpiryDate();
        LocalDate bExpiry = bLine.getExpiryDate();
        if (aExpiry != null && bExpiry == null) {
            return true;
        }
        if (aExpiry != null && aExpiry.isBefore(bExpiry)) {
            return true;
        }
        boolean sameExpiry = aExpiry == null ? bExpiry == null : aExpiry.equals(bExpiry);
        if (!sameExpiry) {
            return false;
        }
        int byReceivedAt = a.getReceivedAt().compareTo(b.getReceivedAt());
        return byReceivedAt < 0 || (byReceivedAt == 0 && a.getId() < b.getId());
    }

    /** Số sổ hiện tại của nguồn kiểm kê. */
    private BigDecimal findBookQuantity(CountLine line) {
        if (line.rawBatchId() != null) {
            return inventoryBatchRepository.findById(line.rawBatchId()).map(InventoryBatch::getQuantityOnHand)
                    .orElseThrow(() -> ApiException.invalidRequest("Không tìm thấy lô kho " + line.rawBatchId()));
        }
        if (line.wipIssueItemId() != null) {
            return issueItemRepository.findById(line.wipIssueItemId()).map(StockIssueItem::getWipRemainingQuantity)
                    .orElseThrow(() -> ApiException.invalidRequest("Không tìm thấy dòng xuất " + line.wipIssueItemId()));
        }
        return kitchenBatchRepository.findById(line.kitchenBatchId()).map(KitchenBatch::getQuantityOnHand)
                .orElseThrow(() -> ApiException.invalidRequest("Không tìm thấy lô bếp " + line.kitchenBatchId()));
    }

    private StockReceiptItem receiptLineOf(long inventoryBatchId) {
        long receiptLineId = inventoryBatchRepository.findById(inventoryBatchId).orElseThrow().getStockReceiptItemId();
        return receiptItemRepository.findById(receiptLineId).orElseThrow();
    }

    private StockIssue findIssueForUpdate(long issueId) {
        return issueRepository.findByIdForUpdate(issueId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu xuất " + issueId));
    }

    static void requireOneSource(Long raw, Long wip, Long kitchen) {
        int n = (raw == null ? 0 : 1) + (wip == null ? 0 : 1) + (kitchen == null ? 0 : 1);
        if (n != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Mỗi dòng cần đúng một nguồn: rawBatchId, wipIssueItemId hoặc kitchenBatchId");
        }
    }

    static <T> List<Long> idsOf(List<T> lines, Function<T, Long> id) {
        List<Long> ids = lines.stream().map(id).filter(java.util.Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    static <T> Map<Long, T> idMap(List<T> entities, Function<T, Long> id) {
        return entities.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
