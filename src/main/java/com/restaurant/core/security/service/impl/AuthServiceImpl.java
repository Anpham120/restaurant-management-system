package com.restaurant.core.security.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.security.JwtTokenService;
import com.restaurant.core.security.LoginThrottle;
import com.restaurant.core.security.dto.request.ChangePasswordRequest;
import com.restaurant.core.security.dto.request.LoginRequest;
import com.restaurant.core.security.dto.response.LoginResponse;
import com.restaurant.core.security.dto.response.MeResponse;
import com.restaurant.core.security.entity.UserCredential;
import com.restaurant.core.security.repository.CredentialQueryRepository;
import com.restaurant.core.security.repository.CredentialQueryRepository.AccountCredentials;
import com.restaurant.core.security.repository.UserCredentialRepository;
import com.restaurant.core.security.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    /** Mọi lý do thất bại đều trả cùng một thông điệp để không lộ username nào tồn tại. */
    private static final String INVALID = "Sai tên đăng nhập hoặc mật khẩu";

    private final CredentialQueryRepository accounts;
    private final UserCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final LoginThrottle throttle;
    /** Hash giả để thời gian kiểm tra không lộ việc username có tồn tại hay không. */
    private final String dummyHash;

    public AuthServiceImpl(CredentialQueryRepository accounts, UserCredentialRepository credentialRepository, PasswordEncoder passwordEncoder,
                           JwtTokenService tokens, LoginThrottle throttle) {
        this.accounts = accounts;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.throttle = throttle;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** Sai quá {@link LoginThrottle} cho phép thì từ chối (429) trước cả khi kiểm mật khẩu, kể cả mật khẩu đúng. */
    @Override
    public LoginResponse login(LoginRequest request) {
        long wait = throttle.lockedSeconds(request.username());
        if (wait > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Đăng nhập sai quá nhiều lần. Thử lại sau " + Math.max(1, (wait + 59) / 60) + " phút");
        }
        AccountCredentials acc = accounts.findByUsername(request.username()).orElse(null);
        boolean passwordOk = passwordEncoder.matches(request.password(), acc == null ? dummyHash : acc.passwordHash());
        if (acc == null || !passwordOk || !"ACTIVE".equals(acc.accountStatus()) || !"ACTIVE".equals(acc.workStatus())) {
            throw invalidCredentialsError(request.username());
        }
        List<String> roles = accounts.findRoleCodes(acc.employeeId());
        if (roles.isEmpty()) {
            throw invalidCredentialsError(request.username());
        }
        throttle.recordSuccess(request.username());
        JwtTokenService.IssuedToken token = tokens.issue(acc.employeeId(), acc.username(), roles);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), acc.employeeId(), acc.username(), roles,
                acc.mustChangePassword());
    }

    private ApiException invalidCredentialsError(String username) {
        throttle.recordFailure(username);
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID);
    }

    /** Kiểm mật khẩu cũ, lưu hash mới, tắt cờ bắt buộc đổi. Token đang dùng vẫn hợp lệ tới khi hết hạn. */
    @Override
    @Transactional
    public void changePassword(long employeeId, ChangePasswordRequest request) {
        String hash = accounts.findPasswordHash(employeeId).orElse(dummyHash);
        if (!passwordEncoder.matches(request.currentPassword(), hash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Mật khẩu hiện tại không đúng");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "SAME_PASSWORD", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        UserCredential credential = credentialRepository.findByEmployeeIdForUpdate(employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Mật khẩu hiện tại không đúng"));
        credential.changePassword(passwordEncoder.encode(request.newPassword()));
        credentialRepository.flush();
    }

    @Override
    public MeResponse getCurrentAccount(long employeeId, String username, List<String> roles) {
        String fullName = accounts.findFullName(employeeId).orElse(null);
        return new MeResponse(employeeId, username, fullName, roles);
    }
}
