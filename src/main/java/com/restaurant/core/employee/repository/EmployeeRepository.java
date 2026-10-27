package com.restaurant.core.employee.repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.employee.dto.EmployeeDtos.DepartmentResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.EmployeeResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.RoleResponse;

/** Nhân viên, tài khoản, vai trò, phòng ban — rc_app ghi trực tiếp (roles chỉ đọc). Owner: Nguyễn Quang Hiếu. */
@Repository
public class EmployeeRepository {

    /** Dòng SQL trung gian: vai trò gộp thành chuỗi rồi tách ở {@link #toResponse}. */
    record EmployeeRow(long id, String employeeCode, String fullName, String gender, LocalDate dateOfBirth, String phone, String email,
                       String address, LocalDate hireDate, LocalDate endDate, String workStatus, long departmentId,
                       String departmentName, Long accountId, String username, String accountStatus, Boolean mustChangePassword,
                       String roleCodes) {
    }

    private static final String EMPLOYEE_SELECT = """
            SELECT e.id, e.employee_code, e.full_name, e.gender, e.date_of_birth, e.phone, e.email, e.address, e.hire_date,
                   e.end_date, e.work_status, e.department_id, d.department_name, a.id AS account_id, a.username,
                   a.account_status, a.must_change_password,
                   (SELECT string_agg(r.role_code, ',' ORDER BY r.role_code)
                      FROM account_roles ar JOIN roles r ON r.id = ar.role_id WHERE ar.account_id = a.id) AS role_codes
              FROM employees e
              JOIN departments d ON d.id = e.department_id
              LEFT JOIN accounts a ON a.employee_id = e.id
            """;

    private final JdbcClient jdbc;

    public EmployeeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- phòng ban, vai trò
    public List<DepartmentResponse> findDepartments() {
        return jdbc.sql("SELECT id, department_name AS name FROM departments ORDER BY department_name").query(DepartmentResponse.class).list();
    }

    public long insertDepartment(String name) {
        return jdbc.sql("INSERT INTO departments (department_name) VALUES (:n) RETURNING id").param("n", name).query(Long.class).single();
    }

    public int updateDepartment(long id, String name) {
        return jdbc.sql("UPDATE departments SET department_name = :n WHERE id = :id").param("id", id).param("n", name).update();
    }

    public List<RoleResponse> findRoles() {
        return jdbc.sql("SELECT id, role_code, role_name FROM roles ORDER BY id").query(RoleResponse.class).list();
    }

    // ---------- nhân viên
    public Optional<EmployeeResponse> findEmployee(long id) {
        return jdbc.sql(EMPLOYEE_SELECT + " WHERE e.id = :id").param("id", id).query(EmployeeRow.class).optional()
                .map(EmployeeRepository::toResponse);
    }

    public List<EmployeeResponse> findEmployees(String status, int page, int size) {
        return jdbc.sql(EMPLOYEE_SELECT + " WHERE CAST(:s AS text) IS NULL OR e.work_status = :s ORDER BY e.id DESC LIMIT :size OFFSET :off")
                .param("s", status).param("size", size).param("off", (long) page * size).query(EmployeeRow.class).list()
                .stream().map(EmployeeRepository::toResponse).toList();
    }

    public long countEmployees(String status) {
        return jdbc.sql("SELECT count(*) FROM employees WHERE CAST(:s AS text) IS NULL OR work_status = :s").param("s", status)
                .query(Long.class).single();
    }

    public long insertEmployee(String code, String fullName, long departmentId, String gender, LocalDate dateOfBirth, String phone,
                               String email, String address, LocalDate hireDate) {
        return jdbc.sql("""
                        INSERT INTO employees (employee_code, full_name, department_id, gender, date_of_birth, phone, email, address, hire_date)
                        VALUES (:c, :n, :d, :g, :b, :p, :m, :a, :h) RETURNING id""")
                .param("c", code).param("n", fullName).param("d", departmentId).param("g", gender).param("b", dateOfBirth)
                .param("p", phone).param("m", email).param("a", address).param("h", hireDate).query(Long.class).single();
    }

    public int updateEmployee(long id, String fullName, long departmentId, String gender, LocalDate dateOfBirth, String phone,
                              String email, String address, LocalDate hireDate, String workStatus) {
        return jdbc.sql("""
                        UPDATE employees SET full_name = :n, department_id = :d, gender = :g, date_of_birth = :b, phone = :p, email = :m,
                               address = :a, hire_date = :h, work_status = :w
                         WHERE id = :id AND work_status <> 'LEFT'""")
                .param("id", id).param("n", fullName).param("d", departmentId).param("g", gender).param("b", dateOfBirth)
                .param("p", phone).param("m", email).param("a", address).param("h", hireDate).param("w", workStatus).update();
    }

    public int markLeft(long id, LocalDate endDate) {
        return jdbc.sql("UPDATE employees SET work_status = 'LEFT', end_date = :d WHERE id = :id AND work_status <> 'LEFT'")
                .param("id", id).param("d", endDate).update();
    }

    // ---------- tài khoản
    public long insertAccount(long employeeId, String username, String passwordHash) {
        return jdbc.sql("""
                        INSERT INTO accounts (employee_id, username, password_hash, must_change_password)
                        VALUES (:e, :u, :h, true) RETURNING id""")
                .param("e", employeeId).param("u", username).param("h", passwordHash).query(Long.class).single();
    }

    public Optional<Long> findEmployeeIdOfAccount(long accountId) {
        return jdbc.sql("SELECT employee_id FROM accounts WHERE id = :id FOR UPDATE").param("id", accountId).query(Long.class).optional();
    }

    /** Số vai trò hợp lệ trong danh sách (để phát hiện mã vai trò không tồn tại). */
    public int countRoles(List<String> roleCodes) {
        return jdbc.sql("SELECT count(*) FROM roles WHERE role_code = ANY (:codes)").param("codes", roleCodes.toArray(String[]::new))
                .query(Integer.class).single();
    }

    public void replaceRoles(long accountId, List<String> roleCodes) {
        jdbc.sql("DELETE FROM account_roles WHERE account_id = :a AND role_id NOT IN (SELECT id FROM roles WHERE role_code = ANY (:codes))")
                .param("a", accountId).param("codes", roleCodes.toArray(String[]::new)).update();
        jdbc.sql("""
                        INSERT INTO account_roles (account_id, role_id) SELECT :a, id FROM roles WHERE role_code = ANY (:codes)
                        ON CONFLICT (account_id, role_id) DO NOTHING""")
                .param("a", accountId).param("codes", roleCodes.toArray(String[]::new)).update();
    }

    /** Đổi trạng thái tài khoản nếu đang ở trạng thái from. Trả số dòng. */
    public int setAccountStatus(long accountId, String from, String to) {
        return jdbc.sql("UPDATE accounts SET account_status = :t WHERE id = :id AND account_status = :f")
                .param("id", accountId).param("f", from).param("t", to).update();
    }

    public int resetPassword(long accountId, String passwordHash) {
        return jdbc.sql("UPDATE accounts SET password_hash = :h, must_change_password = true WHERE id = :id")
                .param("id", accountId).param("h", passwordHash).update();
    }

    private static EmployeeResponse toResponse(EmployeeRow r) {
        List<String> roles = r.roleCodes() == null ? List.of() : Arrays.asList(r.roleCodes().split(","));
        return new EmployeeResponse(r.id(), r.employeeCode(), r.fullName(), r.gender(), r.dateOfBirth(), r.phone(), r.email(), r.address(),
                r.hireDate(), r.endDate(), r.workStatus(), r.departmentId(), r.departmentName(), r.accountId(), r.username(),
                r.accountStatus(), r.mustChangePassword(), roles);
    }
}
