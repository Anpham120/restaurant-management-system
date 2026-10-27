package com.restaurant.core.employee.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.employee.dto.ChangePasswordRequest;
import com.restaurant.core.employee.dto.LoginRequest;
import com.restaurant.core.employee.dto.LoginResponse;
import com.restaurant.core.employee.dto.MeResponse;
import com.restaurant.core.employee.repository.AccountRepository;
import com.restaurant.core.employee.repository.AccountRepository.AccountCredentials;
import com.restaurant.core.security.JwtTokenService;

@Service
public class AuthService {

    /** Mọi lý do thất bại đều trả cùng một thông điệp để không lộ username nào tồn tại. */
    private static final String INVALID = "Sai tên đăng nhập hoặc mật khẩu";

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    /** Hash giả để thời gian kiểm tra không lộ việc username có tồn tại hay không. */
    private final String dummyHash;

    public AuthService(AccountRepository accounts, PasswordEncoder passwordEncoder, JwtTokenService tokens) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public LoginResponse login(LoginRequest request) {
        AccountCredentials acc = accounts.findByUsername(request.username()).orElse(null);
        boolean passwordOk = passwordEncoder.matches(request.password(), acc == null ? dummyHash : acc.passwordHash());
        if (acc == null || !passwordOk || !"ACTIVE".equals(acc.accountStatus()) || !"ACTIVE".equals(acc.workStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID);
        }
        List<String> roles = accounts.findRoleCodes(acc.employeeId());
        if (roles.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID);
        }
        JwtTokenService.IssuedToken token = tokens.issue(acc.employeeId(), acc.username(), roles);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), acc.employeeId(), acc.username(), roles,
                acc.mustChangePassword());
    }

    /** Kiểm mật khẩu cũ, lưu hash mới, tắt cờ bắt buộc đổi. Token đang dùng vẫn hợp lệ tới khi hết hạn. */
    public void changePassword(long employeeId, ChangePasswordRequest request) {
        String hash = accounts.findPasswordHash(employeeId).orElse(dummyHash);
        if (!passwordEncoder.matches(request.currentPassword(), hash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Mật khẩu hiện tại không đúng");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "SAME_PASSWORD", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        accounts.changePassword(employeeId, passwordEncoder.encode(request.newPassword()));
    }

    public MeResponse me(long employeeId, String username, List<String> roles) {
        String fullName = accounts.findFullName(employeeId).orElse(null);
        return new MeResponse(employeeId, username, fullName, roles);
    }
}
