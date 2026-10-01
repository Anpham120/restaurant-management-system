package vn.bnn.rms.payment.entity;

import java.time.Instant;
import java.util.Locale;

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

import vn.bnn.rms.payment.dto.PaymentDtos.SepayWebhookRequest;
import vn.bnn.rms.payment.enums.MatchStatus;

/** A bank movement reported by SePay. Kept even when it matches nothing, so a cashier can check it. */
@Entity
@Table(name = "bank_transaction")
@Getter
@Setter
@NoArgsConstructor
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SePay transaction id; unique, so the same webhook is processed once (BR-16). */
    @Column(nullable = false, unique = true)
    private String providerTxnId;

    private String gateway;

    private String accountNumber;

    @Column(nullable = false)
    private long amount;

    private String content;

    private String code;

    @Column(nullable = false)
    private String transferType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus matchStatus;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    public static BankTransaction from(SepayWebhookRequest request) {
        BankTransaction tx = new BankTransaction();
        tx.providerTxnId = String.valueOf(request.id());
        tx.gateway = cut(request.gateway(), 50);
        tx.accountNumber = cut(request.accountNumber(), 30);
        tx.amount = request.transferAmount() == null ? 0 : request.transferAmount();
        tx.content = cut(request.content(), 500);
        tx.code = cut(request.code(), 50);
        tx.transferType = request.transferType() == null ? "unknown"
                : cut(request.transferType().toLowerCase(Locale.ROOT), 10);
        return tx;
    }

    public void resolve(MatchStatus status, Payment payment, String note) {
        this.matchStatus = status;
        this.payment = payment;
        this.note = cut(note, 200);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
