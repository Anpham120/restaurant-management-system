package com.restaurant.core.employee.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.restaurant.core.employee.dto.EmployeeDtos.DepartmentRequest;
import com.restaurant.core.employee.dto.EmployeeDtos.DepartmentResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.EmployeeResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.RoleResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.restaurant.core.employee.repository.EmployeeRepository;

/**
 * Hồ sơ nhân viên, tài khoản, vai trò. Bảng ghi trực tiếp → @PreAuthorize ở controller là lớp kiểm quyền; ở đây chặn
 * thêm việc Manager tự khóa / tự nghỉ / tự bỏ vai trò MANAGER của chính mình. Owner: Nguyễn Quang Hiếu.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository repo;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(EmployeeRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    public List<DepartmentResponse> departments() {
        return repo.findDepartments();
    }

    public DepartmentResponse createDepartment(DepartmentRequest r) {
        long id = repo.insertDepartment(r.name().strip());
        return new DepartmentResponse(id, r.name().strip());
    }

    public DepartmentResponse updateDepartment(long id, DepartmentRequest r) {
        if (repo.updateDepartment(id, r.name().strip()) == 0) {
            throw notFound("Không tìm thấy phòng ban " + id);
        }
        return new DepartmentResponse(id, r.name().strip());
    }

    public List<RoleResponse> roles() {
        return repo.findRoles();
    }

    public PageResponse<EmployeeResponse> employees(String status, int page, int size) {
        return new PageResponse<>(repo.findEmployees(status, page, size), page, size, repo.countEmployees(status));
    }

    public EmployeeResponse employee(long id) {
        return repo.findEmployee(id).orElseThrow(() -> notFound("Không tìm thấy nhân viên " + id));
    }

    /** Ba bảng (employees, accounts, account_roles) → một transaction. */
    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest r) {
        List<String> roles = normalizeRoles(r.roles());
        long id = repo.insertEmployee(r.employeeCode().strip(), r.fullName().strip(), r.departmentId(), blank(r.gender()), r.dateOfBirth(),
                blank(r.phone()), blank(r.email()), blank(r.address()), r.hireDate());
        long accountId = repo.insertAccount(id, r.username().strip(), passwordEncoder.encode(r.initialPassword()));
        repo.replaceRoles(accountId, roles);
        return employee(id);
    }

    public EmployeeResponse update(long id, long actorId, UpdateEmployeeRequest r) {
        if (id == actorId && !"ACTIVE".equals(r.workStatus())) {
            throw selfLockout();
        }
        if (repo.updateEmployee(id, r.fullName().strip(), r.departmentId(), blank(r.gender()), r.dateOfBirth(), blank(r.phone()),
                blank(r.email()), blank(r.address()), r.hireDate(), r.workStatus()) == 0) {
            employee(id);
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_LEFT", "Nhân viên " + id + " đã nghỉ việc, không sửa hồ sơ");
        }
        return employee(id);
    }

    /** Sau khi nghỉ: đăng nhập bị từ chối, token cũ gọi rc_* nhận RC403 (rc_authorize kiểm work_status). */
    public EmployeeResponse leave(long id, long actorId, java.time.LocalDate endDate) {
        if (id == actorId) {
            throw selfLockout();
        }
        if (repo.markLeft(id, endDate) == 0) {
            employee(id);
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_LEFT", "Nhân viên " + id + " đã nghỉ việc");
        }
        return employee(id);
    }

    @Transactional
    public EmployeeResponse replaceRoles(long accountId, long actorId, List<String> requested) {
        List<String> roles = normalizeRoles(requested);
        long employeeId = accountOwner(accountId);
        if (employeeId == actorId && !roles.contains("MANAGER")) {
            throw selfLockout();
        }
        repo.replaceRoles(accountId, roles);
        return employee(employeeId);
    }

    public EmployeeResponse lock(long accountId, long actorId) {
        long employeeId = accountOwner(accountId);
        if (employeeId == actorId) {
            throw selfLockout();
        }
        if (repo.setAccountStatus(accountId, "ACTIVE", "LOCKED") == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_ACTIVE", "Tài khoản " + accountId + " không ở trạng thái ACTIVE");
        }
        return employee(employeeId);
    }

    public EmployeeResponse unlock(long accountId) {
        long employeeId = accountOwner(accountId);
        if (repo.setAccountStatus(accountId, "LOCKED", "ACTIVE") == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_LOCKED", "Tài khoản " + accountId + " không bị khóa");
        }
        return employee(employeeId);
    }

    /** Đặt mật khẩu tạm; người dùng phải đổi khi đăng nhập lại. */
    public EmployeeResponse resetPassword(long accountId, String newPassword) {
        long employeeId = accountOwner(accountId);
        repo.resetPassword(accountId, passwordEncoder.encode(newPassword));
        return employee(employeeId);
    }

    private long accountOwner(long accountId) {
        return repo.findEmployeeIdOfAccount(accountId).orElseThrow(() -> notFound("Không tìm thấy tài khoản " + accountId));
    }

    private List<String> normalizeRoles(List<String> requested) {
        List<String> roles = requested.stream().map(s -> s.strip().toUpperCase()).distinct().toList();
        if (repo.countRoles(roles) != roles.size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "UNKNOWN_ROLE", "Vai trò không tồn tại trong " + roles);
        }
        return roles;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static ApiException selfLockout() {
        return new ApiException(HttpStatus.CONFLICT, "SELF_LOCKOUT", "Không được tự khóa, tự cho nghỉ hoặc tự bỏ vai trò MANAGER của chính mình");
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
