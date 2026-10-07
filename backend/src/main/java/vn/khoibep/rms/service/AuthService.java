package vn.khoibep.rms.service;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.security.RateLimiter;
import vn.khoibep.rms.config.JwtTokenService;
import vn.khoibep.rms.dto.AuthDtos.ChangePasswordRequest;
import vn.khoibep.rms.dto.AuthDtos.LoginRequest;
import vn.khoibep.rms.dto.AuthDtos.LoginResponse;
import vn.khoibep.rms.dto.EmployeeDtos.EmployeeDto;
import vn.khoibep.rms.model.Employee;
import vn.khoibep.rms.repository.EmployeeRepository;

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

    /** FR-01.6, BR-41: every other session ends; this one goes on with the new token returned. */
    @Transactional
    public LoginResponse changePassword(Long employeeId, ChangePasswordRequest request) {
        Employee employee = get(employeeId);
        if (!passwordEncoder.matches(request.currentPassword(), employee.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        employee.changePassword(passwordEncoder.encode(request.newPassword()));
        return new LoginResponse(tokenService.issue(employee), EmployeeDto.from(employee));
    }

    /** FR-01.7, BR-41: every session ends, this one too. */
    @Transactional
    public void logoutEverywhere(Long employeeId) {
        get(employeeId).revokeTokens();
    }

    private Employee get(Long id) {
        return employees.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
    }
}
