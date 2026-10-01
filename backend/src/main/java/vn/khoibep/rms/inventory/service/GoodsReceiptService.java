package vn.khoibep.rms.inventory.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.util.DateRange;
import vn.khoibep.rms.employee.repository.EmployeeRepository;
import vn.khoibep.rms.inventory.dto.PurchaseDtos.GoodsReceiptDto;
import vn.khoibep.rms.inventory.dto.PurchaseDtos.GoodsReceiptRequest;
import vn.khoibep.rms.inventory.dto.PurchaseDtos.ReceiptLineRequest;
import vn.khoibep.rms.inventory.entity.GoodsReceipt;
import vn.khoibep.rms.inventory.entity.InventoryItem;
import vn.khoibep.rms.inventory.entity.ReceiptLine;
import vn.khoibep.rms.inventory.entity.StockMovement;
import vn.khoibep.rms.inventory.entity.Supplier;
import vn.khoibep.rms.inventory.repository.GoodsReceiptRepository;
import vn.khoibep.rms.inventory.repository.InventoryItemRepository;
import vn.khoibep.rms.inventory.repository.StockMovementRepository;
import vn.khoibep.rms.inventory.repository.SupplierRepository;

/** FR-09.6, FR-09.7: goods bought with their prices; stock and unit cost follow (BR-19, BR-37). */
@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    /** One look covers at most about a quarter. */
    static final int MAX_DAYS = 92;

    private final GoodsReceiptRepository receipts;
    private final SupplierRepository suppliers;
    private final InventoryItemRepository items;
    private final StockMovementRepository movements;
    private final EmployeeRepository employees;
    private final Clock clock;

    /** Newest first; the days are dates in Vietnam. */
    @Transactional(readOnly = true)
    public List<GoodsReceiptDto> list(LocalDate from, LocalDate to) {
        DateRange.check(from, to, MAX_DAYS);
        ZoneId zone = clock.getZone();
        return receipts.findBetween(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().map(GoodsReceiptDto::from).toList();
    }

    @Transactional(readOnly = true)
    public GoodsReceiptDto get(Long id) {
        return GoodsReceiptDto.from(receipts.findWithLinesById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập")));
    }

    /** BR-37: saved once and for good; each line brings stock in and averages the unit cost. */
    @Transactional
    public GoodsReceiptDto create(GoodsReceiptRequest request, Long employeeId) {
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
        if (!supplier.isActive()) {
            throw ApiException.conflict("Nhà cung cấp đã ngừng giao dịch");
        }
        // Lock the ingredients in id order, so two receipts saved at once cannot wait on each other.
        Map<Long, InventoryItem> locked = new HashMap<>();
        request.lines().stream().map(ReceiptLineRequest::inventoryItemId).distinct().sorted()
                .forEach(id -> locked.put(id, items.findByIdForUpdate(id)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"))));
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        GoodsReceipt receipt = new GoodsReceipt(supplier, note, employees.getReferenceById(employeeId),
                clock.instant());
        List<ReceiptLine> lines = request.lines().stream()
                .map(l -> receipt.addLine(locked.get(l.inventoryItemId()), l.quantity(), l.unitPrice()))
                .toList();
        receipts.save(receipt);
        for (ReceiptLine line : lines) {
            line.getItem().receive(line.getQuantity(), line.getUnitPrice());
            movements.save(StockMovement.received(line, employeeId));
        }
        return GoodsReceiptDto.from(receipt);
    }
}
