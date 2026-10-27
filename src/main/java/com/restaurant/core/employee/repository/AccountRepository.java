package com.restaurant.core.employee.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Đọc tài khoản đăng nhập (bảng accounts, employees, account_roles, roles — module employee sở hữu). */
@Repository
public class AccountRepository {

    /** Dữ liệu cần để xác thực. passwordHash chỉ dùng trong AuthService, không bao giờ trả ra API hay ghi log. */
    public record AccountCredentials(long employeeId, String username, String passwordHash, String accountStatus,
                                     String workStatus, boolean mustChangePassword) {
    }

    /** Trạng thái hiện tại để kiểm mỗi request (token còn hạn không có nghĩa tài khoản còn dùng được). */
    public record AccountState(String accountStatus, String workStatus, boolean mustChangePassword, String roleCodes) {
    }

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccountCredentials> findByUsername(String username) {
        return jdbc.sql("""
                        SELECT e.id AS employee_id, a.username, a.password_hash, a.account_status,
                               e.work_status, a.must_change_password
                          FROM accounts a
                          JOIN employees e ON e.id = a.employee_id
                         WHERE a.username = :username
                        """)
                .param("username", username)
                .query(AccountCredentials.class)
                .optional();
    }

    public Optional<AccountState> findState(long employeeId) {
        return jdbc.sql("""
                        SELECT a.account_status, e.work_status, a.must_change_password,
                               (SELECT string_agg(r.role_code, ',' ORDER BY r.role_code)
                                  FROM account_roles ar JOIN roles r ON r.id = ar.role_id WHERE ar.account_id = a.id) AS role_codes
                          FROM accounts a
                          JOIN employees e ON e.id = a.employee_id
                         WHERE a.employee_id = :e
                        """)
                .param("e", employeeId)
                .query(AccountState.class)
                .optional();
    }

    /** Mật khẩu hiện tại của chính người đăng nhập (đổi mật khẩu). */
    public Optional<String> findPasswordHash(long employeeId) {
        return jdbc.sql("SELECT password_hash FROM accounts WHERE employee_id = :e").param("e", employeeId).query(String.class).optional();
    }

    public int changePassword(long employeeId, String passwordHash) {
        return jdbc.sql("UPDATE accounts SET password_hash = :h, must_change_password = false WHERE employee_id = :e")
                .param("e", employeeId).param("h", passwordHash).update();
    }

    public List<String> findRoleCodes(long employeeId) {
        return jdbc.sql("""
                        SELECT r.role_code
                          FROM accounts a
                          JOIN account_roles ar ON ar.account_id = a.id
                          JOIN roles r ON r.id = ar.role_id
                         WHERE a.employee_id = :employeeId
                         ORDER BY r.role_code
                        """)
                .param("employeeId", employeeId)
                .query(String.class)
                .list();
    }

    public Optional<String> findFullName(long employeeId) {
        return jdbc.sql("SELECT full_name FROM employees WHERE id = :id")
                .param("id", employeeId)
                .query(String.class)
                .optional();
    }
}
