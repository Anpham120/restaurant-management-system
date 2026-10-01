package vn.khoibep.rms.order.service;

import java.time.Clock;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.audit.enums.AuditAction;
import vn.khoibep.rms.audit.service.AuditService;
import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvent;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.employee.enums.Role;
import vn.khoibep.rms.employee.repository.EmployeeRepository;
import vn.khoibep.rms.order.dto.OrderDtos.AdjustmentDto;
import vn.khoibep.rms.order.dto.OrderDtos.AdjustmentRequest;
import vn.khoibep.rms.order.dto.OrderDtos.OrderDto;
import vn.khoibep.rms.order.entity.Adjustment;
import vn.khoibep.rms.order.entity.Order;
import vn.khoibep.rms.order.entity.OrderItem;
import vn.khoibep.rms.order.enums.AdjustmentReason;
import vn.khoibep.rms.order.enums.AdjustmentStatus;
import vn.khoibep.rms.order.enums.AdjustmentType;
import vn.khoibep.rms.order.repository.AdjustmentRepository;
import vn.khoibep.rms.order.repository.OrderRepository;
import vn.khoibep.rms.payment.service.PaymentService;

/** FR-08.10, FR-08.11, BR-35: discounts and dishes given free, approved by a manager past the limit. */
@Service
@RequiredArgsConstructor
public class AdjustmentService {

    /** BR-35: past either limit a cashier needs a manager. */
    static final int LIMIT_PERCENT = 10;
    static final long LIMIT_AMOUNT = 150_000;

    private final AdjustmentRepository adjustments;
    private final OrderRepository orders;
    private final EmployeeRepository employees;
    private final PaymentService payments;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final RealtimeEvents realtime;
    private final Clock clock;

    @Transactional
    public OrderDto create(Long orderId, AdjustmentRequest request) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn"));
        requireOpen(order);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        if (request.reason() == AdjustmentReason.OTHER && note == null) {
            throw ApiException.badRequest("Lý do khác cần ghi chú");
        }
        OrderItem dish = null;
        long amount;
        if (request.type() == AdjustmentType.COMP) {
            dish = order.getItems().stream().filter(i -> i.getId().equals(request.orderItemId())).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Chọn một món của đơn này để tặng"));
            if (!dish.getStatus().isBillable()) {
                throw ApiException.conflict("Chỉ tặng được món đã xác nhận và chưa huỷ");
            }
            OrderItem given = dish;
            if (order.getAdjustments().stream().anyMatch(a -> a.isOpen() && a.gives(given))) {
                throw ApiException.conflict("Món này đã được tặng");
            }
            amount = dish.lineTotal();
        } else {
            if (request.amount() == null) {
                throw ApiException.badRequest("Nhập số tiền giảm");
            }
            amount = request.amount();
        }
        long open = order.getAdjustments().stream().filter(Adjustment::isOpen).mapToLong(Adjustment::getAmount).sum()
                + amount;
        long subtotal = order.subtotal();
        if (open > subtotal) {
            throw ApiException.badRequest("Tổng giảm vượt tiền món");
        }
        boolean withinLimit = open * 100 <= subtotal * LIMIT_PERCENT && open <= LIMIT_AMOUNT;
        AdjustmentStatus status = withinLimit || currentUser.hasRole(Role.MANAGER)
                ? AdjustmentStatus.APPLIED : AdjustmentStatus.PENDING;
        Adjustment adjustment = adjustments.save(new Adjustment(order, dish, request.type(), amount, request.reason(),
                note, status, employees.getReferenceById(currentUser.id()), clock.instant()));
        order.getAdjustments().add(adjustment);
        if (adjustment.isInEffect()) {
            takeEffect(order, adjustment);
        }
        publish(order);
        return OrderDto.from(order);
    }

    /** FR-08.11: the requests managers have to decide, oldest first. */
    @Transactional(readOnly = true)
    public List<AdjustmentDto> pending() {
        return adjustments.findWithStatus(AdjustmentStatus.PENDING).stream().map(AdjustmentDto::from).toList();
    }

    @Transactional
    public AdjustmentDto approve(Long id) {
        Order order = lockOrderOf(id);
        Adjustment adjustment = pendingOn(order, id);
        // Dishes may have been cancelled since the request.
        if (order.discountTotal() + adjustment.getAmount() > order.subtotal()) {
            throw ApiException.conflict("Tổng giảm vượt tiền món");
        }
        adjustment.decide(AdjustmentStatus.APPLIED, employees.getReferenceById(currentUser.id()), clock.instant());
        takeEffect(order, adjustment);
        publish(order);
        return AdjustmentDto.from(adjustment);
    }

    @Transactional
    public AdjustmentDto reject(Long id) {
        Order order = lockOrderOf(id);
        Adjustment adjustment = pendingOn(order, id);
        adjustment.decide(AdjustmentStatus.REJECTED, employees.getReferenceById(currentUser.id()), clock.instant());
        publish(order);
        return AdjustmentDto.from(adjustment);
    }

    /** A request withdrawn, or a discount taken back, while the bill is still open. */
    @Transactional
    public OrderDto cancel(Long id) {
        Order order = lockOrderOf(id);
        Adjustment adjustment = on(order, id);
        if (!adjustment.isOpen()) {
            throw ApiException.conflict("Khoản giảm đã bị từ chối hoặc đã huỷ");
        }
        boolean wasInEffect = adjustment.isInEffect();
        adjustment.cancel();
        if (wasInEffect) {
            // BR-14: the total went up, so a pending transfer code no longer matches it.
            payments.cancelPendingTransfers(order.getId());
        }
        publish(order);
        return OrderDto.from(order);
    }

    /**
     * BR-14: the total went down, so a pending transfer code no longer matches it. BR-34: logged by whoever made it
     * take effect, the cashier within the limit or the manager who approved.
     */
    private void takeEffect(Order order, Adjustment adjustment) {
        payments.cancelPendingTransfers(order.getId());
        String subject = adjustment.getItem() == null ? "Giảm giá bill"
                : "Tặng " + adjustment.getItem().getItemName() + " x" + adjustment.getItem().getQuantity();
        String reason = adjustment.getReason().getLabel()
                + (adjustment.getNote() == null ? "" : ": " + adjustment.getNote());
        audit.record(AuditAction.DISCOUNT_GIVEN, order, subject, null, null, adjustment.getAmount(), reason);
    }

    /** Locks the order first, as every payment path does, so limits and totals are read once at a time. */
    private Order lockOrderOf(Long adjustmentId) {
        Long orderId = adjustments.findOrderIdById(adjustmentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản giảm"));
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        requireOpen(order);
        return order;
    }

    private Adjustment on(Order order, Long id) {
        return order.getAdjustments().stream().filter(a -> a.getId().equals(id)).findFirst().orElseThrow();
    }

    private Adjustment pendingOn(Order order, Long id) {
        Adjustment adjustment = on(order, id);
        if (adjustment.getStatus() != AdjustmentStatus.PENDING) {
            throw ApiException.conflict("Khoản giảm không còn chờ duyệt");
        }
        return adjustment;
    }

    private static void requireOpen(Order order) {
        if (!order.isOpen()) {
            throw ApiException.conflict("Đơn đã đóng");
        }
    }

    private void publish(Order order) {
        realtime.orderChanged(order.getId(), order.tableId(), order.guestToken());
        realtime.staffNotice(RealtimeEvent.ADJUSTMENTS_CHANGED);
    }
}
