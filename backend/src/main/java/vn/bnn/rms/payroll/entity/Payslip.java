package vn.bnn.rms.payroll.entity;

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

import vn.bnn.rms.employee.entity.Employee;
import vn.bnn.rms.employee.enums.PayType;

/** One person's pay for one month. Pay type and rate are copied when calculated (BR-26). */
@Entity
@Table(name = "payslip")
@Getter
@Setter
@NoArgsConstructor
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_id")
    private Payroll payroll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayType payType;

    @Column(nullable = false)
    private long payRate;

    @Column(nullable = false)
    private int workedMinutes;

    @Column(nullable = false)
    private int workDays;

    @Column(nullable = false)
    private int paidLeaveDays;

    @Column(nullable = false)
    private long baseAmount;

    /** Bonuses minus deductions. */
    @Column(nullable = false)
    private long adjustmentAmount;

    @Column(nullable = false)
    private long netAmount;

    public Payslip(Payroll payroll, Employee employee) {
        this.payroll = payroll;
        this.employee = employee;
    }

    /** Net pay is always base pay plus adjustments. */
    public void setAmounts(long base, long adjustments) {
        this.baseAmount = base;
        this.adjustmentAmount = adjustments;
        this.netAmount = base + adjustments;
    }
}
