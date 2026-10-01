package vn.bnn.rms.auth.service;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.auth.dto.AuthDtos.ChangePasswordRequest;
import vn.bnn.rms.auth.dto.AuthDtos.LoginRequest;
import vn.bnn.rms.auth.dto.AuthDtos.LoginResponse;
import vn.bnn.rms.common.exception.ApiException;
import vn.bnn.rms.common.security.RateLimiter;
import vn.bnn.rms.config.JwtTokenService;
import vn.bnn.rms.employee.dto.EmployeeDtos.EmployeeDto;
import vn.bnn.rms.employee.entity.Employee;
import vn.bnn.rms.employee.repository.EmployeeRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** BR-31: sign-in attempts per user name each minute, right or wrong. */
    static final int LOGINS_PER_MINUTE = 10;

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RateLimiter rateLimiter;

    /**
     * FR-01.1, BR-03: a wrong password never reveals which part was wrong; locked accounts cannot sign in.
     * FR-01.5, BR-31: the limit is checked first, so guessing costs no password check once it is reached.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        rateLimiter.acquire("login:" + username, LOGINS_PER_MINUTE,
                "Đăng nhập quá nhiều lần, vui lòng thử lại sau %d giây");
        Employee employee = employees.findByUsername(username)
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
