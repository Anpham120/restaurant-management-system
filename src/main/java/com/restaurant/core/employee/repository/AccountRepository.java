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
