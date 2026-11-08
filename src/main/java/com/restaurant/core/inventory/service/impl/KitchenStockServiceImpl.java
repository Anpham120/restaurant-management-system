package com.restaurant.core.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.inventory.OrderItemStockFlags;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.AdjustmentLine;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.AdjustmentRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.AdjustmentResponse;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.MarkUnusableResponse;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.OutputView;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.PreparationOutput;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.PreparationRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.PreparationResponse;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.ReturnLine;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.ReturnRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.ReturnResponse;
import com.restaurant.core.inventory.entity.DocumentStatus;
import com.restaurant.core.inventory.entity.FulfillmentMode;
import com.restaurant.core.inventory.entity.InventoryBatch;
import com.restaurant.core.inventory.entity.IssueStatus;
import com.restaurant.core.inventory.entity.KitchenBatch;
import com.restaurant.core.inventory.entity.KitchenIngredientType;
import com.restaurant.core.inventory.entity.KitchenPreparation;
import com.restaurant.core.inventory.entity.KitchenPreparationOutput;
import com.restaurant.core.inventory.entity.MovementType;
import com.restaurant.core.inventory.entity.ReservationStatus;
import com.restaurant.core.inventory.entity.StockAdjustment;
import com.restaurant.core.inventory.entity.StockAdjustmentItem;
import com.restaurant.core.inventory.entity.StockIssueItem;
import com.restaurant.core.inventory.entity.StockMovement;
import com.restaurant.core.inventory.entity.StockReceiptItem;
import com.restaurant.core.inventory.entity.StockReturn;
import com.restaurant.core.inventory.entity.StockReturnItem;
import com.restaurant.core.inventory.repository.InventoryBatchRepository;
import com.restaurant.core.inventory.repository.KitchenBatchRepository;
import com.restaurant.core.inventory.repository.KitchenIngredientTypeRepository;
import com.restaurant.core.inventory.repository.KitchenPreparationOutputRepository;
import com.restaurant.core.inventory.repository.KitchenPreparationRepository;
import com.restaurant.core.inventory.repository.KitchenStockQueryRepository;
import com.restaurant.core.inventory.repository.StockAdjustmentItemRepository;
import com.restaurant.core.inventory.repository.StockAdjustmentRepository;
import com.restaurant.core.inventory.repository.StockIssueItemRepository;
import com.restaurant.core.inventory.repository.StockIssueRepository;
import com.restaurant.core.inventory.repository.StockMovementRepository;
import com.restaurant.core.inventory.repository.StockReceiptItemRepository;
import com.restaurant.core.inventory.repository.StockReservationRepository;
import com.restaurant.core.inventory.repository.StockReturnItemRepository;
import com.restaurant.core.inventory.repository.StockReturnRepository;
import com.restaurant.core.inventory.service.KitchenStockService;

import static com.restaurant.core.inventory.service.impl.StockDocumentServiceImpl.blankToNull;
import static com.restaurant.core.inventory.service.impl.StockDocumentServiceImpl.idMap;
import static com.restaurant.core.inventory.service.impl.StockDocumentServiceImpl.idsOf;
import static com.restaurant.core.inventory.service.impl.StockDocumentServiceImpl.plain;
import static com.restaurant.core.inventory.service.impl.StockDocumentServiceImpl.requireOneSource;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KitchenStockServiceImpl implements KitchenStockService {

    private final KitchenStockQueryRepository kitchenQueryRepository;
    private final KitchenPreparationRepository preparationRepository;
    private final KitchenPreparationOutputRepository preparationOutputRepository;
    private final StockReturnRepository returnRepository;
    private final StockReturnItemRepository returnItemRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final StockAdjustmentItemRepository adjustmentItemRepository;
    private final StockIssueRepository issueRepository;
    private final StockIssueItemRepository issueItemRepository;
    private final StockReceiptItemRepository receiptItemRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final KitchenBatchRepository kitchenBatchRepository;
    private final KitchenIngredientTypeRepository kitchenTypeRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMovementRepository movementRepository;
    private final OrderItemStockFlags orderItemStockFlags;
    private final BusinessDateProvider clock;

    // ==================== sơ chế

    @Override
    @Transactional
    public PreparationResponse createPreparation(long actorId, PreparationRequest request) {
        Optional<KitchenPreparation> existing = preparationRepository.findByRequestKey(request.requestKey());
        if (existing.isPresent()) {
            return getPreparationById(existing.get().getId());
        }
        KitchenPreparation preparation = preparationRepository.save(new KitchenPreparation(issueItemRepository.getReferenceById(request.stockIssueItemId()), request.inputQuantity(),
                blankToNull(request.lossReason()), actorId, request.requestKey()));
        for (PreparationOutput output : request.outputs()) {
            preparationOutputRepository.save(new KitchenPreparationOutput(preparation, kitchenTypeRepository.getReferenceById(output.kitchenIngredientTypeId()),
                    output.outputQuantity(), output.usableUntil()));
        }
        preparationOutputRepository.flush();
        return getPreparationById(preparation.getId());
    }

    @Override
    @Transactional
    public PreparationResponse confirmPreparation(long preparationId, long actorId) {
        KitchenPreparation preparation = preparationRepository.findByIdForUpdate(preparationId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu sơ chế " + preparationId));
        if (preparation.getStatus() != DocumentStatus.DRAFT) {
            throw ApiException.invalidState("Phiếu sơ chế " + preparationId + " đang " + preparation.getStatus() + ", không phải nháp");
        }
        StockIssueItem wipLine = issueItemRepository.lockIssueItemsByIds(List.of(preparation.getStockIssueItemId())).getFirst();
        IssueStatus issueStatus = issueRepository.findById(wipLine.getStockIssueId()).orElseThrow().getStatus();
        if (wipLine.getFulfillmentMode() != FulfillmentMode.PREPARE || issueStatus != IssueStatus.RECEIVED) {
            throw ApiException.invalidState("Dòng xuất " + wipLine.getId() + " không phải hàng chờ sơ chế đã nhận");
        }
        if (preparation.getInputQuantity().compareTo(wipLine.getWipRemainingQuantity()) > 0) {
            throw ApiException.invalidState("Đầu vào " + plain(preparation.getInputQuantity()) + " vượt lượng chờ sơ chế còn "
                    + plain(wipLine.getWipRemainingQuantity()));
        }
        List<KitchenPreparationOutput> outputs = preparationOutputRepository.findByKitchenPreparation_IdOrderById(preparationId);
        BigDecimal totalOutput = outputs.stream().map(KitchenPreparationOutput::getOutputQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (outputs.isEmpty() || totalOutput.signum() <= 0 || totalOutput.compareTo(preparation.getInputQuantity()) > 0) {
            throw ApiException.invalidRequest("Tổng đầu ra " + plain(totalOutput) + " phải lớn hơn 0 và không vượt đầu vào "
                    + plain(preparation.getInputQuantity()));
        }
        StockReceiptItem rawSource = receiptLineOfInventoryBatch(wipLine.getInventoryBatchId());
        Map<Long, KitchenIngredientType> types = idMap(kitchenTypeRepository.findAllById(
                outputs.stream().map(KitchenPreparationOutput::getKitchenIngredientTypeId).toList()), KitchenIngredientType::getId);
        if (types.values().stream().anyMatch(t -> !KitchenIngredientType.PREPARED.equals(t.getHandlingType())
                || !t.getRawIngredientId().equals(rawSource.getIngredientId()))) {
            throw ApiException.invalidRequest("Đầu ra phải là loại đã sơ chế (PREPARED) của cùng nguyên liệu gốc");
        }
        Instant now = clock.now();
        Instant rawExpiryLimit = ShelfLifeCalculator.calculateUsableUntil(rawSource.getExpiryDate(), null, now);
        if (rawExpiryLimit != null && outputs.stream().anyMatch(o -> o.getUsableUntil() != null && o.getUsableUntil().isAfter(rawExpiryLimit))) {
            throw ApiException.invalidRequest("Hạn dùng đầu ra vượt hạn lô nguyên liệu gốc");
        }

        wipLine.changeWipRemaining(preparation.getInputQuantity().negate());
        movementRepository.save(StockMovement.ofWipIssueItem(MovementType.PREP_INPUT, wipLine, preparation.getInputQuantity().negate(),
                "PREP_INPUT:" + preparationId, now).preparation(preparation));
        for (KitchenPreparationOutput output : outputs) {
            Instant usableUntil = output.getUsableUntil() != null ? output.getUsableUntil()
                    : ShelfLifeCalculator.calculateUsableUntil(rawSource.getExpiryDate(), types.get(output.getKitchenIngredientTypeId()).getShelfLifeHours(), now);
            if (output.getUsableUntil() == null && usableUntil != null) {
                output.setUsableUntil(usableUntil);
            }
            KitchenBatch batch = kitchenBatchRepository.save(KitchenBatch.fromPreparationOutput(output.getKitchenIngredientType(), output,
                    usableUntil, output.getOutputQuantity(), now));
            movementRepository.save(StockMovement.ofKitchenBatch(MovementType.PREP_OUTPUT, batch, output.getOutputQuantity(),
                    "PREP_OUTPUT:" + output.getId(), now).preparation(preparation));
        }
        preparationOutputRepository.flush();
        preparation.confirm(actorId, now);
        preparationRepository.flush();
        return getPreparationById(preparationId);
    }

    @Override
    public PreparationResponse getPreparationById(long preparationId) {
        PreparationResponse.Header h = kitchenQueryRepository.findPreparation(preparationId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu sơ chế " + preparationId));
        List<OutputView> outputs = kitchenQueryRepository.findPreparationOutputs(preparationId);
        BigDecimal out = outputs.stream().map(OutputView::outputQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PreparationResponse(h.id(), h.status(), h.stockIssueItemId(), h.inputQuantity(), out, h.inputQuantity().subtract(out),
                h.lossReason(), h.preparedById(), h.preparedAt(), h.requestKey(), outputs);
    }

    @Override
    public List<PreparationResponse.Header> getPreparations(String status, int page, int size) {
        return kitchenQueryRepository.findPreparations(status, page, size);
    }

    // ==================== hoàn trả

    @Override
    @Transactional
    public ReturnResponse createStockReturn(long actorId, ReturnRequest request) {
        Optional<StockReturn> existing = returnRepository.findByRequestKey(request.requestKey());
        if (existing.isPresent()) {
            return getStockReturnById(existing.get().getId());
        }
        StockReturn stockReturn = returnRepository.save(new StockReturn(actorId, request.reason().strip(), request.requestKey(), clock.now()));
        for (ReturnLine line : request.items()) {
            returnItemRepository.save(new StockReturnItem(stockReturn, issueItemRepository.getReferenceById(line.sourceIssueItemId()), referenceOrNull(kitchenBatchRepository, line.directKitchenBatchId()), line.quantity()));
        }
        returnItemRepository.flush();
        return getStockReturnById(stockReturn.getId());
    }

    @Override
    @Transactional
    public ReturnResponse confirmStockReturn(long returnId, long actorId) {
        StockReturn stockReturn = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu hoàn trả " + returnId));
        if (stockReturn.getStatus() != DocumentStatus.DRAFT) {
            throw ApiException.invalidState("Phiếu hoàn trả " + returnId + " đang " + stockReturn.getStatus() + ", không phải nháp");
        }
        List<StockReturnItem> lines = returnItemRepository.findByStockReturn_IdOrderById(returnId);
        if (lines.isEmpty()) {
            throw ApiException.invalidRequest("Phiếu hoàn trả " + returnId + " rỗng");
        }
        // khóa đúng thứ tự chung: lô kho chính → dòng xuất → lô bếp
        List<Long> sourceLineIds = lines.stream().map(StockReturnItem::getSourceIssueItemId).distinct().toList();
        Map<Long, InventoryBatch> rawBatches = idMap(inventoryBatchRepository.lockBatchesByIds(
                issueItemRepository.findInventoryBatchIdsOfIssueItems(sourceLineIds)), InventoryBatch::getId);
        Map<Long, StockIssueItem> sourceLines = idMap(issueItemRepository.lockIssueItemsByIds(sourceLineIds), StockIssueItem::getId);
        Map<Long, KitchenBatch> kitchenBatches = idMap(kitchenBatchRepository.lockBatchesByIds(
                idsOf(lines, StockReturnItem::getDirectKitchenBatchId)), KitchenBatch::getId);
        Instant now = clock.now();
        for (StockReturnItem line : lines) {
            StockIssueItem source = sourceLines.get(line.getSourceIssueItemId());
            if (issueRepository.findById(source.getStockIssueId()).orElseThrow().getStatus() != IssueStatus.RECEIVED) {
                throw ApiException.invalidState("Dòng trả " + line.getId() + ": chỉ trả hàng của phiếu xuất bếp đã nhận");
            }
            if (source.getFulfillmentMode() == FulfillmentMode.PREPARE) {
                takeBackFromWip(line, source, now);
            } else {
                takeBackFromKitchenBatch(line, kitchenBatches, now);
            }
            rawBatches.get(source.getInventoryBatchId()).changeOnHand(line.getQuantity());
            movementRepository.save(StockMovement.ofRawBatch(MovementType.RETURN, source.getInventoryBatch(), line.getQuantity(),
                    "RETURN-IN:" + line.getId(), now).returnItem(line));
        }
        inventoryBatchRepository.flush();
        stockReturn.confirm(actorId, now);
        returnRepository.flush();
        return getStockReturnById(returnId);
    }

    private void takeBackFromWip(StockReturnItem line, StockIssueItem source, Instant now) {
        if (line.getDirectKitchenBatchId() != null) {
            throw ApiException.invalidRequest("Dòng trả " + line.getId() + ": hàng chờ sơ chế không kèm lô bếp");
        }
        if (source.getWipRemainingQuantity().compareTo(line.getQuantity()) < 0) {
            throw ApiException.invalidState("Dòng trả " + line.getId() + ": vượt lượng chờ sơ chế còn lại");
        }
        source.changeWipRemaining(line.getQuantity().negate());
        movementRepository.save(StockMovement.ofWipIssueItem(MovementType.RETURN, source, line.getQuantity().negate(),
                "RETURN-OUT:" + line.getId(), now).returnItem(line));
    }

    private void takeBackFromKitchenBatch(StockReturnItem line, Map<Long, KitchenBatch> kitchenBatches, Instant now) {
        if (line.getDirectKitchenBatchId() == null) {
            throw ApiException.invalidRequest("Dòng trả " + line.getId() + ": hàng dùng thẳng phải chỉ rõ lô bếp");
        }
        KitchenBatch batch = kitchenBatches.get(line.getDirectKitchenBatchId());
        if (!batch.isUsable() || batch.getFreeQuantity().compareTo(line.getQuantity()) < 0) {
            throw ApiException.invalidState("Dòng trả " + line.getId() + ": vượt lượng khả dụng (phần đang giữ cho món không trả được)");
        }
        batch.changeOnHand(line.getQuantity().negate());
        movementRepository.save(StockMovement.ofKitchenBatch(MovementType.RETURN, batch, line.getQuantity().negate(),
                "RETURN-OUT:" + line.getId(), now).returnItem(line));
    }

    @Override
    public ReturnResponse getStockReturnById(long returnId) {
        ReturnResponse.Header h = kitchenQueryRepository.findReturn(returnId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu hoàn trả " + returnId));
        return new ReturnResponse(h.id(), h.status(), h.reason(), h.requestedById(), h.confirmedById(), h.createdAt(), h.confirmedAt(),
                h.requestKey(), kitchenQueryRepository.findReturnLines(returnId));
    }

    @Override
    public List<ReturnResponse.Header> getStockReturns(String status, int page, int size) {
        return kitchenQueryRepository.findReturns(status, page, size);
    }

    // ==================== hủy hàng

    @Override
    @Transactional
    public AdjustmentResponse createStockAdjustment(long actorId, AdjustmentRequest request) {
        request.items().forEach(l -> requireOneSource(l.rawBatchId(), l.wipIssueItemId(), l.kitchenBatchId()));
        Optional<StockAdjustment> existing = adjustmentRepository.findByRequestKey(request.requestKey());
        if (existing.isPresent()) {
            return getStockAdjustmentById(existing.get().getId());
        }
        StockAdjustment adjustment = adjustmentRepository.save(new StockAdjustment(request.adjustmentType(), request.reason().strip(), actorId,
                request.requestKey(), clock.now()));
        for (AdjustmentLine line : request.items()) {
            adjustmentItemRepository.save(new StockAdjustmentItem(adjustment, referenceOrNull(inventoryBatchRepository, line.rawBatchId()),
                    referenceOrNull(issueItemRepository, line.wipIssueItemId()), referenceOrNull(kitchenBatchRepository, line.kitchenBatchId()),
                    line.quantity().negate(), blankToNull(line.note())));
        }
        adjustmentItemRepository.flush();
        return getStockAdjustmentById(adjustment.getId());
    }

    @Override
    @Transactional
    public AdjustmentResponse confirmStockAdjustment(long adjustmentId, long actorId) {
        StockAdjustment adjustment = adjustmentRepository.findByIdForUpdate(adjustmentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu hủy hàng " + adjustmentId));
        if (adjustment.getStatus() != DocumentStatus.DRAFT) {
            throw ApiException.invalidState("Phiếu hủy hàng " + adjustmentId + " đang " + adjustment.getStatus() + ", không phải nháp");
        }
        List<StockAdjustmentItem> lines = adjustmentItemRepository.findByStockAdjustment_IdOrderById(adjustmentId);
        if (lines.isEmpty()) {
            throw ApiException.invalidRequest("Phiếu hủy hàng " + adjustmentId + " rỗng");
        }
        Map<Long, InventoryBatch> rawBatches = idMap(inventoryBatchRepository.lockBatchesByIds(idsOf(lines, StockAdjustmentItem::getRawBatchId)),
                InventoryBatch::getId);
        Map<Long, StockIssueItem> wipLines = idMap(issueItemRepository.lockIssueItemsByIds(idsOf(lines, StockAdjustmentItem::getWipIssueItemId)),
                StockIssueItem::getId);
        Map<Long, KitchenBatch> kitchenBatches = idMap(kitchenBatchRepository.lockBatchesByIds(idsOf(lines, StockAdjustmentItem::getKitchenBatchId)),
                KitchenBatch::getId);
        Instant now = clock.now();
        for (StockAdjustmentItem line : lines) {
            BigDecimal delta = line.getQuantityDelta();
            StockMovement movement;
            if (line.getRawBatchId() != null) {
                InventoryBatch batch = rawBatches.get(line.getRawBatchId());
                requireEnough(line, batch.getQuantityOnHand().add(delta).signum() >= 0);
                batch.changeOnHand(delta);
                movement = StockMovement.ofRawBatch(MovementType.WASTE, batch, delta, "WASTE:" + line.getId(), now);
            } else if (line.getWipIssueItemId() != null) {
                StockIssueItem wip = wipLines.get(line.getWipIssueItemId());
                requireEnough(line, wip.getWipRemainingQuantity().add(delta).signum() >= 0);
                wip.changeWipRemaining(delta);
                movement = StockMovement.ofWipIssueItem(MovementType.WASTE, wip, delta, "WASTE:" + line.getId(), now);
            } else {
                KitchenBatch batch = kitchenBatches.get(line.getKitchenBatchId());
                requireEnough(line, batch.getQuantityOnHand().add(delta).compareTo(batch.getReservedQuantity()) >= 0);
                batch.changeOnHand(delta);
                movement = StockMovement.ofKitchenBatch(MovementType.WASTE, batch, delta, "WASTE:" + line.getId(), now);
            }
            movementRepository.save(movement.adjustmentItem(line).reason(adjustment.getAdjustmentType()));
        }
        inventoryBatchRepository.flush();
        adjustment.confirm(actorId, now);
        adjustmentRepository.flush();
        return getStockAdjustmentById(adjustmentId);
    }

    private static void requireEnough(StockAdjustmentItem line, boolean enough) {
        if (!enough) {
            throw ApiException.invalidState("Dòng hủy " + line.getId() + ": vượt lượng khả dụng (lô bếp: phải cấp lại món đang giữ trước)");
        }
    }

    @Override
    public AdjustmentResponse getStockAdjustmentById(long adjustmentId) {
        AdjustmentResponse.Header h = kitchenQueryRepository.findAdjustment(adjustmentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu hủy hàng " + adjustmentId));
        return new AdjustmentResponse(h.id(), h.status(), h.adjustmentType(), h.reason(), h.createdById(), h.confirmedById(),
                h.createdAt(), h.confirmedAt(), h.requestKey(), kitchenQueryRepository.findAdjustmentLines(adjustmentId));
    }

    @Override
    public List<AdjustmentResponse.Header> getStockAdjustments(String status, int page, int size) {
        return kitchenQueryRepository.findAdjustments(status, page, size);
    }

    // ==================== lô hỏng

    @Override
    @Transactional
    public MarkUnusableResponse markBatchUnusable(String kind, long batchId, long actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalidRequest("Đánh dấu lô không dùng được bắt buộc có lý do");
        }
        Instant now = clock.now();
        switch (kind) {
            case "RAW" -> {
                InventoryBatch batch = inventoryBatchRepository.lockBatchesByIds(List.of(batchId)).stream().findFirst()
                        .filter(InventoryBatch::isUsable)
                        .orElseThrow(() -> ApiException.invalidState("Lô RAW " + batchId + " không tồn tại hoặc đã đánh dấu"));
                batch.markUnusable(reason.strip(), actorId, now);
                inventoryBatchRepository.flush();
                return new MarkUnusableResponse(kind, batchId, 0);
            }
            case "KITCHEN" -> {
                // khóa món đang giữ lô TRƯỚC rồi mới khóa lô (thứ tự khóa chung: món → lô bếp)
                orderItemStockFlags.lockOrderItems(reservationRepository.findOrderItemIdsHoldingBatch(batchId, ReservationStatus.ACTIVE));
                KitchenBatch batch = kitchenBatchRepository.findByIdForUpdate(batchId).filter(KitchenBatch::isUsable)
                        .orElseThrow(() -> ApiException.invalidState("Lô KITCHEN " + batchId + " không tồn tại hoặc đã đánh dấu"));
                batch.markUnusable(reason.strip(), actorId, now);
                kitchenBatchRepository.flush();
                List<Long> affectedItems = reservationRepository.findOrderItemIdsHoldingBatch(batchId, ReservationStatus.ACTIVE);
                orderItemStockFlags.markStockException(affectedItems);
                return new MarkUnusableResponse(kind, batchId, affectedItems.size());
            }
            default -> throw ApiException.invalidRequest("Loại lô phải là RAW hoặc KITCHEN");
        }
    }

    private StockReceiptItem receiptLineOfInventoryBatch(long inventoryBatchId) {
        return receiptItemRepository.findById(inventoryBatchRepository.findById(inventoryBatchId).orElseThrow().getStockReceiptItemId())
                .orElseThrow();
    }

    /** Tham chiếu tới dòng có id cho trước (không truy vấn); id null (cột khóa ngoại không bắt buộc) thì trả null. */
    private static <T> T referenceOrNull(JpaRepository<T, Long> repository, Long id) {
        return id == null ? null : repository.getReferenceById(id);
    }
}
