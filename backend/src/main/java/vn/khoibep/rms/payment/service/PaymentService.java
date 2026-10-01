package vn.khoibep.rms.payment.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.audit.enums.AuditAction;
import vn.khoibep.rms.audit.service.AuditService;
import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.util.Money;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.order.entity.Order;
import vn.khoibep.rms.order.enums.ItemStatus;
import vn.khoibep.rms.order.enums.OrderStatus;
import vn.khoibep.rms.order.repository.OrderRepository;
import vn.khoibep.rms.payment.dto.PaymentDtos.BankTransactionDto;
import vn.khoibep.rms.payment.dto.PaymentDtos.PaymentDto;
import vn.khoibep.rms.payment.dto.PaymentDtos.PaymentInstruction;
import vn.khoibep.rms.payment.entity.CashShift;
import vn.khoibep.rms.payment.entity.Payment;
import vn.khoibep.rms.payment.enums.Confirmation;
import vn.khoibep.rms.payment.enums.MatchStatus;
import vn.khoibep.rms.payment.enums.PaymentStatus;
import vn.khoibep.rms.payment.repository.BankTransactionRepository;
import vn.khoibep.rms.payment.repository.PaymentRepository;
import vn.khoibep.rms.reservation.repository.ReservationRepository;
import vn.khoibep.rms.settings.entity.RestaurantSettings;
import vn.khoibep.rms.settings.service.SettingsService;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository payments;
    private final ReservationRepository reservations;
    private final CashShiftService cashShifts;
    private final BankTransactionRepository bankTransactions;
    private final OrderRepository orders;
    private final SettingsService settings;
    private final AuditService audit;
    private final RealtimeEvents realtime;

    /**
     * FR-08.2, BR-13: cash must cover what is taken; once the bill is covered the order closes and the table becomes
     * free. FR-08.12, BR-43: amount is a part of the bill, the whole rest when null.
     * BR-39: the cash goes into the drawer of the open shift.
     */
    @Transactional
    public PaymentDto payCash(Long orderId, Long amount, long receivedAmount, Long cashierId) {
        Order order = lockPayable(orderId);
        long part = part(order, amount);
        if (receivedAmount < part) {
            throw ApiException.badRequest("Tiền khách đưa nhỏ hơn số tiền cần thu");
        }
        CashShift shift = cashShifts.lockOpen();
        cancelPendingTransfers(orderId);
        Payment payment = payments.save(Payment.cash(order, part, receivedAmount, cashierId, shift));
        settle(order);
        return PaymentDto.from(payment);
    }

    /** FR-08.5: the guest pays what is left. */
    @Transactional
    public PaymentInstruction requestTransfer(Long orderId) {
        return requestTransfer(orderId, null);
    }

    /**
     * FR-08.3, BR-14: one pending transfer per order. Asking again returns the same code while the amount is
     * unchanged; otherwise the old code is cancelled and a new one is issued. BR-43: amount as for cash.
     */
    @Transactional
    public PaymentInstruction requestTransfer(Long orderId, Long amount) {
        Order order = lockPayable(orderId);
        long part = part(order, amount);
        if (part <= 0) {
            throw ApiException.conflict("Đơn không còn gì phải trả");
        }
        RestaurantSettings bank = settings.current();
        if (!bank.hasBankAccount()) {
            throw ApiException.conflict("Nhà hàng chưa cấu hình tài khoản nhận chuyển khoản");
        }
        Payment pending = payments.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING).orElse(null);
        if (pending != null && pending.getAmount() == part) {
            return instruction(pending, bank);
        }
        if (pending != null) {
            pending.setStatus(PaymentStatus.CANCELLED);
            // Write the cancel before inserting the new one: only one PENDING row per order is allowed.
            payments.flush();
        }
        Payment payment = payments.save(Payment.transfer(order, part, newReference()));
        return instruction(payment, bank);
    }

    /** FR-08.6, BR-17: a cashier confirms a transfer after checking the bank app. */
    @Transactional
    public PaymentDto confirmManually(Long paymentId, Long employeeId) {
        Long orderId = payments.findById(paymentId).map(p -> p.getOrder().getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản thanh toán"));
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        Payment payment = payments.findById(paymentId).orElseThrow();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.conflict("Khoản thanh toán không còn ở trạng thái chờ");
        }
        if (!order.isOpen()) {
            throw ApiException.conflict("Đơn đã đóng");
        }
        payment.markPaid(Confirmation.MANUAL, employeeId);
        // BR-34: money taken as received without the bank's word for it.
        audit.record(AuditAction.MANUAL_CONFIRMATION, order, payment.getReference(), null, null, payment.getAmount(),
                null);
        settle(order);
        return PaymentDto.from(payment);
    }

    /** FR-08.9, FR-08.13, BR-33: every payment a receipt lists, once the bill is covered. */
    @Transactional(readOnly = true)
    public List<PaymentDto> paidPayments(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn"));
        if (order.getStatus() != OrderStatus.PAID) {
            throw ApiException.notFound("Đơn chưa thanh toán xong");
        }
        return payments.findByOrderIdAndStatusOrderByPaidAtAscIdAsc(orderId, PaymentStatus.PAID).stream()
                .map(PaymentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BankTransactionDto> bankTransactions(MatchStatus status) {
        return bankTransactions.findByMatchStatusOrderByReceivedAtDesc(status).stream()
                .map(BankTransactionDto::from).toList();
    }

    /** BR-14: a pending code is void as soon as the bill changes. */
    @Transactional
    public int cancelPendingTransfers(Long orderId) {
        return payments.updateStatusByOrderId(orderId, PaymentStatus.PENDING, PaymentStatus.CANCELLED);
    }

    /** Called by the webhook with the order row already locked. */
    void completeTransfer(Payment payment, Order order) {
        payment.markPaid(Confirmation.AUTO, null);
        settle(order);
    }

    /** BR-13, BR-43: the whole rest by default; a part is more than zero and no more than the rest. */
    private static long part(Order order, Long amount) {
        long due = order.due();
        if (amount == null) {
            return due;
        }
        if (amount > due) {
            throw ApiException.badRequest("Số tiền thu lớn hơn số còn phải trả (" + Money.vnd(due) + ")");
        }
        if (amount <= 0 && due > 0) {
            throw ApiException.badRequest("Số tiền thu phải lớn hơn 0");
        }
        return amount;
    }

    /** BR-43: the order closes once what was taken covers the bill; until then every screen shows what is left. */
    private void settle(Order order) {
        if (order.due() == 0) {
            close(order);
        } else {
            realtime.orderChanged(order.getId(), order.tableId(), order.guestTokens());
        }
    }

    private Order lockPayable(Long orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn"));
        if (!order.isOpen()) {
            throw ApiException.conflict("Đơn đã đóng");
        }
        if (order.countItems(ItemStatus.PENDING) > 0) {
            throw ApiException.conflict("Còn món chờ xác nhận, hãy xác nhận hoặc từ chối trước khi thanh toán");
        }
        if (order.countPendingAdjustments() > 0) {
            throw ApiException.conflict("Còn khoản giảm giá chờ quản lý duyệt");
        }
        if (!order.hasBillableItems()) {
            throw ApiException.conflict("Đơn chưa có món tính tiền");
        }
        return order;
    }

    private void close(Order order) {
        // Read before closing: closing gives the tables back (BR-36), and their guest pages are told after.
        List<String> tokens = order.guestTokens();
        order.applyDeposit();
        order.close(OrderStatus.PAID);
        realtime.paymentPaid(order.getId(), order.tableId(), tokens);
    }

    private PaymentInstruction instruction(Payment p, RestaurantSettings bank) {
        return new PaymentInstruction(p.getId(), p.getOrder().getId(), p.getAmount(), p.getReference(),
                VietQr.imageUrl(bank.getBankCode(), bank.getBankAccountNo(), bank.getBankAccountName(),
                        p.getAmount(), p.getReference()),
                bank.getBankCode(), bank.getBankAccountNo(), bank.getBankAccountName());
    }

    private String newReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String reference = PaymentReference.generate();
            // BR-42: booking codes are transfer contents too.
            if (!payments.existsByReference(reference) && !reservations.existsByCode(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not generate a unique payment reference");
    }
}
