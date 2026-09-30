package vn.bnn.rms.inventory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.common.exception.ApiException;
import vn.bnn.rms.employee.entity.Employee;
import vn.bnn.rms.employee.repository.EmployeeRepository;
import vn.bnn.rms.inventory.dto.InventoryDtos.InventoryItemDto;
import vn.bnn.rms.inventory.dto.InventoryDtos.InventoryItemRequest;
import vn.bnn.rms.inventory.dto.InventoryDtos.MovementDto;
import vn.bnn.rms.inventory.dto.InventoryDtos.MovementRequest;
import vn.bnn.rms.inventory.entity.InventoryItem;
import vn.bnn.rms.inventory.entity.StockMovement;
import vn.bnn.rms.inventory.repository.InventoryItemRepository;
import vn.bnn.rms.inventory.repository.StockMovementRepository;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public List<InventoryItemDto> list() {
        return items.findAllByOrderByNameAsc().stream().map(InventoryItemDto::from).toList();
    }

    @Transactional
    public InventoryItemDto create(InventoryItemRequest request) {
        if (items.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("Nguyên liệu đã tồn tại");
        }
        InventoryItem item = new InventoryItem();
        apply(item, request);
        return InventoryItemDto.from(items.save(item));
    }

    /** Name, unit and minimum only; the quantity changes through movements (BR-19). */
    @Transactional
    public InventoryItemDto update(Long id, InventoryItemRequest request) {
        if (items.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw ApiException.conflict("Nguyên liệu đã tồn tại");
        }
        InventoryItem item = items.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
        apply(item, request);
        return InventoryItemDto.from(item);
    }

    /** FR-09.2, BR-19: every change is a movement; stock never goes below zero. */
    @Transactional
    public InventoryItemDto addMovement(Long id, MovementRequest request, Long employeeId) {
        InventoryItem item = items.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
        BigDecimal quantity = request.quantity();
        BigDecimal change = switch (request.type()) {
            case IN -> {
                requirePositive(quantity);
                yield quantity;
            }
            case OUT -> {
                requirePositive(quantity);
                if (quantity.compareTo(item.getQuantity()) > 0) {
                    throw ApiException.conflict("Xuất vượt tồn: chỉ còn "
                            + item.getQuantity().stripTrailingZeros().toPlainString() + " " + item.getUnit());
                }
                yield quantity.negate();
            }
            case ADJUST -> quantity.subtract(item.getQuantity());
        };
        item.setQuantity(item.getQuantity().add(change));
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        movements.save(new StockMovement(item, request.type(), change, note, employeeId));
        return InventoryItemDto.from(item);
    }

    @Transactional(readOnly = true)
    public List<MovementDto> movements(Long id) {
        List<StockMovement> list = movements.findByItemIdOrderByCreatedAtDescIdDesc(id);
        Map<Long, String> names = employees.findAllById(list.stream().map(StockMovement::getCreatedBy)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getId, Employee::getFullName));
        return list.stream().map(m -> new MovementDto(m.getId(), m.getType(), m.getQuantityChange(),
                m.getQuantityAfter(), m.getNote(), names.get(m.getCreatedBy()), m.getCreatedAt())).toList();
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw ApiException.badRequest("Số lượng phải lớn hơn 0");
        }
    }

    private void apply(InventoryItem item, InventoryItemRequest request) {
        item.setName(request.name().trim());
        item.setUnit(request.unit().trim());
        item.setMinQuantity(request.minQuantity());
    }
}
