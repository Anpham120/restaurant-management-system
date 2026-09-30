package vn.bnn.rms.employee;

import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.common.ApiException;
import vn.bnn.rms.employee.EmployeeDtos.CreateEmployeeRequest;
import vn.bnn.rms.employee.EmployeeDtos.EmployeeDto;
import vn.bnn.rms.employee.EmployeeDtos.UpdateEmployeeRequest;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<EmployeeDto> list() {
        return employees.findAll(Sort.by("role", "fullName")).stream().map(EmployeeDto::from).toList();
    }

    /** BR-01: unique username, password hashed with BCrypt. */
    @Transactional
    public EmployeeDto create(CreateEmployeeRequest request) {
        String username = normalize(request.username());
        if (employees.existsByUsername(username)) {
            throw ApiException.conflict("Tên đăng nhập đã tồn tại");
        }
        Employee employee = new Employee(request.fullName().trim(), username,
                passwordEncoder.encode(request.password()), request.role());
        return EmployeeDto.from(employees.save(employee));
    }

    @Transactional
    public EmployeeDto update(Long id, UpdateEmployeeRequest request, Long currentUserId) {
        Employee employee = get(id);
        if (id.equals(currentUserId) && request.role() != employee.getRole()) {
            throw ApiException.conflict("Không thể tự đổi vai trò của chính mình");
        }
        employee.setFullName(request.fullName().trim());
        employee.setRole(request.role());
        return EmployeeDto.from(employee);
    }

    /** BR-03: employees are locked, never deleted. */
    @Transactional
    public EmployeeDto setActive(Long id, boolean active, Long currentUserId) {
        if (!active && id.equals(currentUserId)) {
            throw ApiException.conflict("Không thể tự khoá tài khoản của chính mình");
        }
        Employee employee = get(id);
        employee.setActive(active);
        return EmployeeDto.from(employee);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        get(id).setPasswordHash(passwordEncoder.encode(newPassword));
    }

    static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private Employee get(Long id) {
        return employees.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
    }
}
