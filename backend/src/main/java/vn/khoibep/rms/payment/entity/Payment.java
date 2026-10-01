package vn.khoibep.rms.payment.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import vn.khoibep.rms.order.entity.Order;
import vn.khoibep.rms.payment.enums.Confirmation;
import vn.khoibep.rms.payment.enums.PaymentMethod;
import vn.khoibep.rms.payment.enums.PaymentStatus;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private long amount;

    /** Transfer content the guest must keep, e.g. KB7K3QX9MA (BR-14). Null for cash. */
    private String reference;

    /** Cash handed over by the guest. */
    private Long receivedAmount;

    @Enumerated(EnumType.STRING)
    private Confirmation confirmation;

    private Long confirmedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant paidAt;

    /** The drawer shift that took this cash; null for transfers and for cash taken before shifts (BR-39). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_shift_id")
    private CashShift cashShift;

    public static Payment cash(Order order, long amount, long receivedAmount, Long cashierId, CashShift shift) {
        Payment p = new Payment();
        p.order = order;
        p.method = PaymentMethod.CASH;
        p.amount = amount;
        p.receivedAmount = receivedAmount;
        p.cashShift = shift;
        p.markPaid(Confirmation.MANUAL, cashierId);
        return p;
    }

    public static Payment transfer(Order order, long amount, String reference) {
        Payment p = new Payment();
        p.order = order;
        p.method = PaymentMethod.BANK_TRANSFER;
        p.status = PaymentStatus.PENDING;
        p.amount = amount;
        p.reference = reference;
        return p;
    }

    public void markPaid(Confirmation how, Long employeeId) {
        status = PaymentStatus.PAID;
        confirmation = how;
        confirmedBy = employeeId;
        paidAt = Instant.now();
    }
}
