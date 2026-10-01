package vn.khoibep.rms.employee.entity;

import java.time.Instant;
import java.time.LocalDate;

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

import vn.khoibep.rms.employee.enums.PayType;
import vn.khoibep.rms.employee.enums.Role;

@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Tokens carry the version they were issued with; counting it up revokes all of them (BR-41). */
    @Column(nullable = false)
    private int tokenVersion;

    // Profile and pay (FR-12). Pay is shown to ADMIN only (BR-22).

    private String phone;

    private LocalDate hiredOn;

    /** Set when the person leaves; the account is locked at the same time. */
    private LocalDate leftOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayType payType = PayType.HOURLY;

    /** VND per hour or per month, depending on {@link #payType}. */
    @Column(nullable = false)
    private long payRate;

    public Employee(String fullName, String username, String passwordHash, Role role) {
        this.fullName = fullName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** BR-41: a new password, and every token issued before it stops working. */
    public void changePassword(String newHash) {
        passwordHash = newHash;
        revokeTokens();
    }

    /** BR-41: every token issued until now stops working. */
    public void revokeTokens() {
        tokenVersion++;
    }
}
