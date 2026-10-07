package vn.khoibep.rms.entity;

import java.time.Instant;
import java.time.LocalDate;

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

import vn.khoibep.rms.enums.LeaveStatus;
import vn.khoibep.rms.enums.LeaveType;

/** Days off asked for by an employee, from fromDate to toDate inclusive. */
@Entity
@Table(name = "leave_request")
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType type;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status = LeaveStatus.PENDING;

    private Long decidedBy;

    private String decisionNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant decidedAt;

    public LeaveRequest(Employee employee, LocalDate fromDate, LocalDate toDate, LeaveType type, String reason) {
        this.employee = employee;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.type = type;
        this.reason = reason;
    }
}
