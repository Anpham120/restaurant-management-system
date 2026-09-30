package vn.bnn.rms.auth;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.auth.AuthDtos.ChangePasswordRequest;
import vn.bnn.rms.auth.AuthDtos.LoginRequest;
import vn.bnn.rms.auth.AuthDtos.LoginResponse;
import vn.bnn.rms.common.ApiException;
import vn.bnn.rms.config.JwtTokenService;
import vn.bnn.rms.employee.Employee;
import vn.bnn.rms.employee.EmployeeDtos.EmployeeDto;
import vn.bnn.rms.employee.EmployeeRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    /** FR-01.1, BR-03: a wrong password never reveals which part was wrong; locked accounts cannot sign in. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Employee employee = employees.findByUsername(request.username().trim().toLowerCase(Locale.ROOT))
                .filter(e -> passwordEncoder.matches(request.password(), e.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Sai tên đăng nhập hoặc mật khẩu"));
        if (!employee.isActive()) {
            throw ApiException.forbidden("Tài khoản đã bị khoá");
        }
        return new LoginResponse(tokenService.issue(employee), EmployeeDto.from(employee));
    }

    @Transactional(readOnly = true)
    public EmployeeDto me(Long employeeId) {
        return EmployeeDto.from(get(employeeId));
    }

    @Transactional
    public void changePassword(Long employeeId, ChangePasswordRequest request) {
        Employee employee = get(employeeId);
        if (!passwordEncoder.matches(request.currentPassword(), employee.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        employee.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private Employee get(Long id) {
        return employees.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
    }
}
