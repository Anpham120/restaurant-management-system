package vn.khoibep.rms.entity;

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

import vn.khoibep.rms.enums.MatchStatus;

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

    /** The booking whose deposit this transfer carried, matched or not (BR-42). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    /** A movement as the bank reported it. Texts longer than their columns are cut; no type means "unknown". */
    public static BankTransaction received(String providerTxnId, String gateway, String accountNumber, long amount,
                                           String content, String code, String transferType) {
        BankTransaction tx = new BankTransaction();
        tx.providerTxnId = providerTxnId;
        tx.gateway = cut(gateway, 50);
        tx.accountNumber = cut(accountNumber, 30);
        tx.amount = amount;
        tx.content = cut(content, 500);
        tx.code = cut(code, 50);
        tx.transferType = transferType == null ? "unknown" : cut(transferType.toLowerCase(Locale.ROOT), 10);
        return tx;
    }

    public void resolve(MatchStatus status, Payment payment, String note) {
        this.matchStatus = status;
        this.payment = payment;
        this.note = cut(note, 200);
    }

    /** BR-42: the transfer is about the deposit of a booking. */
    public void resolveDeposit(MatchStatus status, Reservation reservation, String note) {
        resolve(status, null, note);
        this.reservation = reservation;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
