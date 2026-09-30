package vn.bnn.rms.employee;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.common.ApiException;
import vn.bnn.rms.employee.EmployeeDtos.CreateEmployeeRequest;
import vn.bnn.rms.employee.EmployeeDtos.EmployeeDetailDto;
import vn.bnn.rms.employee.EmployeeDtos.ProfileRequest;
import vn.bnn.rms.employee.EmployeeDtos.UpdateEmployeeRequest;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<EmployeeDetailDto> list() {
        return employees.findAll(Sort.by("role", "fullName")).stream().map(EmployeeDetailDto::from).toList();
    }

    /** BR-01: unique username, password hashed with BCrypt. */
    @Transactional
    public EmployeeDetailDto create(CreateEmployeeRequest request) {
        String username = normalize(request.username());
        if (employees.existsByUsername(username)) {
            throw ApiException.conflict("Tên đăng nhập đã tồn tại");
        }
        Employee employee = new Employee(request.fullName().trim(), username,
                passwordEncoder.encode(request.password()), request.role());
        employee.setPhone(blankToNull(request.phone()));
        employee.setHiredOn(request.hiredOn());
        if (request.payType() != null) {
            employee.setPayType(request.payType());
        }
        if (request.payRate() != null) {
            employee.setPayRate(request.payRate());
        }
        return EmployeeDetailDto.from(employees.save(employee));
    }

    @Transactional
    public EmployeeDetailDto update(Long id, UpdateEmployeeRequest request, Long currentUserId) {
        Employee employee = get(id);
        if (id.equals(currentUserId) && request.role() != employee.getRole()) {
            throw ApiException.conflict("Không thể tự đổi vai trò của chính mình");
        }
        employee.setFullName(request.fullName().trim());
        employee.setRole(request.role());
        return EmployeeDetailDto.from(employee);
    }

    /** FR-12.1, FR-12.2 (BR-22). */
    @Transactional
    public EmployeeDetailDto updateProfile(Long id, ProfileRequest request) {
        Employee employee = get(id);
        if (request.hiredOn() != null && employee.getLeftOn() != null
                && request.hiredOn().isAfter(employee.getLeftOn())) {
            throw ApiException.badRequest("Ngày vào làm phải trước ngày nghỉ việc");
        }
        employee.setPhone(blankToNull(request.phone()));
        employee.setHiredOn(request.hiredOn());
        employee.setPayType(request.payType());
        employee.setPayRate(request.payRate());
        return EmployeeDetailDto.from(employee);
    }

    /** FR-12.3: the account is locked at once; profile, attendance and payslips stay (BR-03). */
    @Transactional
    public EmployeeDetailDto resign(Long id, LocalDate leftOn, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw ApiException.conflict("Không thể tự cho mình nghỉ việc");
        }
        Employee employee = get(id);
        if (employee.getHiredOn() != null && leftOn.isBefore(employee.getHiredOn())) {
            throw ApiException.badRequest("Ngày nghỉ việc phải sau ngày vào làm");
        }
        employee.setLeftOn(leftOn);
        employee.setActive(false);
        return EmployeeDetailDto.from(employee);
    }

    /** BR-03: employees are locked, never deleted. Unlocking someone who had left means they are back. */
    @Transactional
    public EmployeeDetailDto setActive(Long id, boolean active, Long currentUserId) {
        if (!active && id.equals(currentUserId)) {
            throw ApiException.conflict("Không thể tự khoá tài khoản của chính mình");
        }
        Employee employee = get(id);
        employee.setActive(active);
        if (active) {
            employee.setLeftOn(null);
        }
        return EmployeeDetailDto.from(employee);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        get(id).setPasswordHash(passwordEncoder.encode(newPassword));
    }

    static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Employee get(Long id) {
        return employees.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
    }
}
