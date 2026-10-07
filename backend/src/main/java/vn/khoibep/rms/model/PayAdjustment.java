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
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A bonus (positive) or deduction (negative) on a payslip, with its reason. */
@Entity
@Table(name = "pay_adjustment")
@Getter
@NoArgsConstructor
public class PayAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id")
    private Payslip payslip;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PayAdjustment(Payslip payslip, long amount, String reason, Long createdBy) {
        this.payslip = payslip;
        this.amount = amount;
        this.reason = reason;
        this.createdBy = createdBy;
    }
}
