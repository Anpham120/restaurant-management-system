package vn.khoibep.rms.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;


/** Cash paid out of the drawer during a shift, with the reason (BR-39). Never changed or deleted. */
@Entity
@Table(name = "cash_expense")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_shift_id")
    private CashShift shift;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    public CashExpense(CashShift shift, long amount, String reason, Employee createdBy, Instant createdAt) {
        this.shift = shift;
        this.amount = amount;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
