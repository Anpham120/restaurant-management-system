package com.restaurant.core.security.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Phần đăng nhập của bảng accounts (mật khẩu băm, cờ phải đổi mật khẩu) mà module Bảo mật được sửa.
 * Hồ sơ tài khoản và vai trò do module Nhân sự quản lý (employee.entity.Account). passwordHash không bao giờ ra API/log.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, insertable = false, updatable = false)
    private Long employeeId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** Tăng lên thì mọi JWT phát trước đó hết hiệu lực (claim "tv" khác giá trị này). */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    /** Người dùng tự đổi mật khẩu: tắt cờ bắt buộc đổi, thu hồi mọi token cũ (kể cả token đã bị lộ). */
    public void changePassword(String newPasswordHash) {
        passwordHash = newPasswordHash;
        mustChangePassword = false;
        revokeTokens();
    }

    /** Đăng xuất phía server: mọi token của tài khoản này hết hiệu lực trên mọi thiết bị. */
    public void revokeTokens() {
        tokenVersion++;
    }
}
