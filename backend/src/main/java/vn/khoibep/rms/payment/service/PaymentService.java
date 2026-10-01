package vn.khoibep.rms.payment.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.audit.enums.AuditAction;
import vn.khoibep.rms.audit.service.AuditService;
import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.order.entity.Order;
import vn.khoibep.rms.order.enums.ItemStatus;
import vn.khoibep.rms.order.enums.OrderStatus;
import vn.khoibep.rms.order.repository.OrderRepository;
import vn.khoibep.rms.payment.dto.PaymentDtos.BankTransactionDto;
import vn.khoibep.rms.payment.dto.PaymentDtos.PaymentDto;
import vn.khoibep.rms.payment.dto.PaymentDtos.PaymentInstruction;
import vn.khoibep.rms.payment.entity.Payment;
import vn.khoibep.rms.payment.enums.Confirmation;
import vn.khoibep.rms.payment.enums.MatchStatus;
import vn.khoibep.rms.payment.enums.PaymentStatus;
import vn.khoibep.rms.payment.repository.BankTransactionRepository;
import vn.khoibep.rms.payment.repository.PaymentRepository;
import vn.khoibep.rms.settings.entity.RestaurantSettings;
import vn.khoibep.rms.settings.service.SettingsService;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository payments;
    private final BankTransactionRepository bankTransactions;
    private final OrderRepository orders;
    private final SettingsService settings;
    private final AuditService audit;
    private final RealtimeEvents realtime;

    /** FR-08.2, BR-13: cash must cover the total; the order closes and the table becomes free. */
    @Transactional
    public PaymentDto payCash(Long orderId, long receivedAmount, Long cashierId) {
        Order order = lockPayable(orderId);
        long total = order.total();
        if (receivedAmount < total) {
            throw ApiException.badRequest("Tiền khách đưa nhỏ hơn tổng tiền");
        }
        cancelPendingTransfers(orderId);
        Payment payment = payments.save(Payment.cash(order, total, receivedAmount, cashierId));
        close(order);
        return PaymentDto.from(payment);
    }

    /**
     * FR-08.3, BR-14: one pending transfer per order. Asking again returns the same code while the
     * total is unchanged; otherwise the old code is cancelled and a new one is issued.
     */
    @Transactional
    public PaymentInstruction requestTransfer(Long orderId) {
        Order order = lockPayable(orderId);
        long total = order.total();
        if (total <= 0) {
            throw ApiException.conflict("Đơn chưa có món tính tiền");
        }
        RestaurantSettings bank = settings.current();
        if (!bank.hasBankAccount()) {
            throw ApiException.conflict("Nhà hàng chưa cấu hình tài khoản nhận chuyển khoản");
        }
        Payment pending = payments.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING).orElse(null);
        if (pending != null && pending.getAmount() == total) {
            return instruction(pending, bank);
        }
        if (pending != null) {
            pending.setStatus(PaymentStatus.CANCELLED);
            // Write the cancel before inserting the new one: only one PENDING row per order is allowed.
            payments.flush();
        }
        Payment payment = payments.save(Payment.transfer(order, total, newReference()));
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
        close(order);
        return PaymentDto.from(payment);
    }

    /** FR-08.9, BR-33: the payment a receipt is printed from, once the order is paid. */
    @Transactional(readOnly = true)
    public PaymentDto paidPayment(Long orderId) {
        return payments.findByOrderIdAndStatus(orderId, PaymentStatus.PAID).map(PaymentDto::from)
                .orElseThrow(() -> ApiException.notFound("Đơn chưa thanh toán"));
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
        close(order);
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
        if (!order.hasBillableItems()) {
            throw ApiException.conflict("Đơn chưa có món tính tiền");
        }
        return order;
    }

    private void close(Order order) {
        order.close(OrderStatus.PAID);
        realtime.paymentPaid(order.getId(), order.tableId(), order.guestToken());
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
            if (!payments.existsByReference(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not generate a unique payment reference");
    }
}
