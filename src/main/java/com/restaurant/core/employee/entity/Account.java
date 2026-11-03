package com.restaurant.core.employee.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bảng accounts (1-1 với employees) và account_roles (n-n với roles).
 * Không có setter: trạng thái và mật khẩu chỉ đổi qua các method nghiệp vụ bên dưới. passwordHash không bao giờ ra API hay log.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(nullable = false, length = 50, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "account_roles",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /** Tài khoản mới luôn phải đổi mật khẩu ở lần đăng nhập đầu. */
    public Account(Employee employee, String username, String passwordHash, Set<Role> roles) {
        this.employee = employee;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles.addAll(roles);
    }

    /** Thay bộ vai trò: chỉ xóa vai trò bị bỏ và thêm vai trò mới, vai trò giữ nguyên không bị ghi lại. */
    public void replaceRoles(Set<Role> newRoles) {
        roles.retainAll(newRoles);
        roles.addAll(newRoles);
    }

    public void lock() {
        status = AccountStatus.LOCKED;
    }

    public void unlock() {
        status = AccountStatus.ACTIVE;
    }

    /** Mật khẩu tạm do quản lý đặt: người dùng phải đổi khi đăng nhập lại. */
    public void resetPassword(String newPasswordHash) {
        passwordHash = newPasswordHash;
        mustChangePassword = true;
    }
}
