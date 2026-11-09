package com.restaurant.core.employee.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.employee.dto.request.CreateEmployeeRequest;
import com.restaurant.core.employee.dto.request.DepartmentRequest;
import com.restaurant.core.employee.dto.request.UpdateEmployeeRequest;
import com.restaurant.core.employee.dto.response.DepartmentResponse;
import com.restaurant.core.employee.dto.response.EmployeeResponse;
import com.restaurant.core.employee.dto.response.RoleResponse;
import com.restaurant.core.employee.entity.Account;
import com.restaurant.core.employee.entity.AccountStatus;
import com.restaurant.core.employee.entity.Department;
import com.restaurant.core.employee.entity.Employee;
import com.restaurant.core.employee.entity.Role;
import com.restaurant.core.employee.entity.WorkStatus;
import com.restaurant.core.employee.mapper.EmployeeMapper;
import com.restaurant.core.employee.repository.AccountRepository;
import com.restaurant.core.employee.repository.DepartmentRepository;
import com.restaurant.core.employee.repository.EmployeeRepository;
import com.restaurant.core.employee.repository.RoleRepository;
import com.restaurant.core.employee.repository.ShiftAssignmentRepository;
import com.restaurant.core.employee.service.EmployeeService;

import static com.restaurant.core.common.util.TextUtils.blankToNull;

/**
 * Nghiệp vụ nhân sự. @PreAuthorize ở controller là lớp kiểm quyền; ở đây chặn thêm việc Manager tự khóa, tự cho nghỉ
 * hoặc tự bỏ vai trò MANAGER của chính mình. Mỗi method ghi chạy trong một transaction và flush trước khi trả về,
 * để lỗi ràng buộc của database (trùng mã, sai ngày…) thành lỗi API rõ ràng thay vì lỗi lúc commit.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    private static final String MANAGER = "MANAGER";

    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final BusinessDateProvider clock;

    // ---------- phòng ban, vai trò

    @Override
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAll(Sort.by("name")).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        return mapper.toResponse(departmentRepository.saveAndFlush(new Department(request.name().strip())));
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(long id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id).orElseThrow(() -> notFoundError("Không tìm thấy phòng ban " + id));
        department.rename(request.name().strip());
        departmentRepository.flush();
        return mapper.toResponse(department);
    }

    @Override
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll(Sort.by("id")).stream().map(mapper::toResponse).toList();
    }

    // ---------- nhân viên

    @Override
    public PageResponse<EmployeeResponse> getEmployees(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Employee> employees = status == null
                ? employeeRepository.findAllBy(pageable)
                : employeeRepository.findByWorkStatus(WorkStatus.valueOf(status), pageable);
        Map<Long, Account> accounts = accountRepository.findByEmployeeIdIn(employees.map(Employee::getId).toList()).stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId(), Function.identity()));
        List<EmployeeResponse> items = employees.stream().map(e -> mapper.toResponse(e, accounts.get(e.getId()))).toList();
        return new PageResponse<>(items, page, size, employees.getTotalElements());
    }

    @Override
    public EmployeeResponse getEmployeeById(long id) {
        Employee employee = employeeRepository.findWithDepartmentById(id).orElseThrow(() -> notFoundError("Không tìm thấy nhân viên " + id));
        return mapper.toResponse(employee, accountRepository.findByEmployeeId(id).orElse(null));
    }

    @Override
    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        Set<Role> roles = findRolesByCodes(request.roles());
        Employee employee = new Employee(request.employeeCode().strip(), request.fullName().strip(),
                departmentRepository.getReferenceById(request.departmentId()));
        applyProfileFields(employee, request.gender(), request.dateOfBirth(), request.phone(), request.email(), request.address(),
                request.hireDate());
        employeeRepository.save(employee);
        accountRepository.save(new Account(employee, request.username().strip(), passwordEncoder.encode(request.initialPassword()), roles));
        accountRepository.flush();
        return getEmployeeById(employee.getId());
    }

    @Override
    @Transactional
    public EmployeeResponse updateEmployee(long id, long actorId, UpdateEmployeeRequest request) {
        WorkStatus newStatus = WorkStatus.valueOf(request.workStatus());
        if (id == actorId && newStatus != WorkStatus.ACTIVE) {
            throw selfLockoutError();
        }
        Employee employee = findEmployeeForUpdate(id);
        if (employee.hasLeft()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_LEFT", "Nhân viên " + id + " đã nghỉ việc, không sửa hồ sơ");
        }
        employee.updateWorkInfo(request.fullName().strip(), departmentRepository.getReferenceById(request.departmentId()), newStatus);
        applyProfileFields(employee, request.gender(), request.dateOfBirth(), request.phone(), request.email(), request.address(),
                request.hireDate());
        employeeRepository.flush();
        return getEmployeeById(id);
    }

    /**
     * Sau khi nghỉ: đăng nhập bị từ chối, token cũ gọi rc_* nhận RC403 (rc_authorize kiểm work_status). Các ca đã xếp
     * sau ngày nghỉ mà chưa chấm công bị hủy kèm lý do, để lịch không còn tính người đã nghỉ.
     */
    @Override
    @Transactional
    public EmployeeResponse markEmployeeAsLeft(long id, long actorId, LocalDate endDate) {
        if (id == actorId) {
            throw selfLockoutError();
        }
        Employee employee = findEmployeeForUpdate(id);
        if (employee.hasLeft()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_LEFT", "Nhân viên " + id + " đã nghỉ việc");
        }
        employee.markAsLeft(endDate);
        employeeRepository.flush();
        cancelShiftsAfterLeaving(id, endDate, actorId);
        return getEmployeeById(id);
    }

    // ---------- tài khoản

    @Override
    @Transactional
    public EmployeeResponse updateAccountRoles(long accountId, long actorId, List<String> roleCodes) {
        Set<Role> roles = findRolesByCodes(roleCodes);
        Account account = findAccountForUpdate(accountId);
        long employeeId = account.getEmployee().getId();
        if (employeeId == actorId && roles.stream().noneMatch(r -> MANAGER.equals(r.getRoleCode()))) {
            throw selfLockoutError();
        }
        account.replaceRoles(roles);
        accountRepository.flush();
        return getEmployeeById(employeeId);
    }

    @Override
    @Transactional
    public EmployeeResponse lockAccount(long accountId, long actorId) {
        Account account = findAccountForUpdate(accountId);
        long employeeId = account.getEmployee().getId();
        if (employeeId == actorId) {
            throw selfLockoutError();
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_ACTIVE", "Tài khoản " + accountId + " không ở trạng thái ACTIVE");
        }
        account.lock();
        accountRepository.flush();
        return getEmployeeById(employeeId);
    }

    @Override
    @Transactional
    public EmployeeResponse unlockAccount(long accountId) {
        Account account = findAccountForUpdate(accountId);
        if (account.getStatus() != AccountStatus.LOCKED) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_LOCKED", "Tài khoản " + accountId + " không bị khóa");
        }
        account.unlock();
        accountRepository.flush();
        return getEmployeeById(account.getEmployee().getId());
    }

    @Override
    @Transactional
    public EmployeeResponse resetAccountPassword(long accountId, String newPassword) {
        Account account = findAccountForUpdate(accountId);
        account.resetPassword(passwordEncoder.encode(newPassword));
        accountRepository.flush();
        return getEmployeeById(account.getEmployee().getId());
    }

    // ---------- hỗ trợ

    /** Các ca đã xếp sau ngày nghỉ mà chưa chấm công bị hủy kèm lý do, để lịch không còn tính người đã nghỉ. */
    private void cancelShiftsAfterLeaving(long employeeId, LocalDate endDate, long actorId) {
        Instant now = clock.now();
        shiftAssignmentRepository.findScheduledWithoutAttendanceAfter(employeeId, endDate)
                .forEach(a -> a.cancel("Nhân viên nghỉ việc từ sau ngày " + endDate, employeeRepository.getReferenceById(actorId), now));
        shiftAssignmentRepository.flush();
    }

    private Employee findEmployeeForUpdate(long id) {
        return employeeRepository.findByIdForUpdate(id).orElseThrow(() -> notFoundError("Không tìm thấy nhân viên " + id));
    }

    private Account findAccountForUpdate(long accountId) {
        return accountRepository.findByIdForUpdate(accountId).orElseThrow(() -> notFoundError("Không tìm thấy tài khoản " + accountId));
    }

    /** Mã vai trò chuẩn hóa (bỏ khoảng trắng, viết hoa); mã không tồn tại → 422. */
    private Set<Role> findRolesByCodes(List<String> requested) {
        Set<String> codes = requested.stream().map(s -> s.strip().toUpperCase()).collect(Collectors.toSet());
        Set<Role> roles = new HashSet<>(roleRepository.findByRoleCodeIn(codes));
        if (roles.size() != codes.size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "UNKNOWN_ROLE", "Vai trò không tồn tại trong " + codes);
        }
        return roles;
    }

    private static void applyProfileFields(Employee e, String gender, LocalDate dateOfBirth, String phone, String email, String address,
                                     LocalDate hireDate) {
        e.updateProfile(blankToNull(gender), dateOfBirth, blankToNull(phone), blankToNull(email), blankToNull(address), hireDate);
    }


    private static ApiException selfLockoutError() {
        return new ApiException(HttpStatus.CONFLICT, "SELF_LOCKOUT", "Không được tự khóa, tự cho nghỉ hoặc tự bỏ vai trò MANAGER của chính mình");
    }

    private static ApiException notFoundError(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
