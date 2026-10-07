package vn.khoibep.rms.model;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import vn.khoibep.rms.enums.AuditAction;

/**
 * One line of the audit log (FR-16, BR-34). Immutable: Hibernate never updates it, and a trigger in the database rejects
 * any UPDATE, DELETE or TRUNCATE. Values are raw: an item status code, or a price in VND.
 */
@Entity
@Table(name = "audit_entry")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /** Null for actions on the menu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private String subject;

    private String beforeValue;

    private String afterValue;

    private Long amount;

    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    public AuditEntry(AuditAction action, Employee employee, Order order, String subject, String beforeValue,
                      String afterValue, Long amount, String reason, Instant createdAt) {
        this.action = action;
        this.employee = employee;
        this.order = order;
        this.subject = subject;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
