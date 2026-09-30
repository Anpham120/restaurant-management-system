package vn.bnn.rms.payroll.entity;

import java.time.Instant;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import vn.bnn.rms.payroll.enums.PayrollStatus;

/** The payroll of one month. */
@Entity
@Table(name = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "YYYY-MM". */
    @Column(nullable = false, unique = true)
    private String period;

    /** Working days in a full month, for monthly pay (BR-26). */
    @Column(nullable = false)
    private int standardDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus status = PayrollStatus.DRAFT;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Long finalizedBy;

    private Instant finalizedAt;

    public Payroll(YearMonth month, int standardDays, Long createdBy) {
        this.period = month.toString();
        this.standardDays = standardDays;
        this.createdBy = createdBy;
    }

    public YearMonth month() {
        return YearMonth.parse(period);
    }
}
