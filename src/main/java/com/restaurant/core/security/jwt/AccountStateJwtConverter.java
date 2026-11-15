package com.restaurant.core.security.jwt;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.restaurant.core.security.repository.CredentialQueryRepository.AccountState;
import com.restaurant.core.security.repository.CredentialQueryRepository;

/**
 * Chữ ký JWT hợp lệ chưa đủ: mỗi request đọc lại trạng thái tài khoản trong DB (một câu SELECT).
 * <ul>
 * <li>Tài khoản bị khóa / nhân viên nghỉ việc / không còn vai trò → 401 ngay, không chờ token hết hạn.</li>
 * <li>Vai trò lấy từ DB, không từ claim "roles" → đổi vai trò có hiệu lực ngay, không chờ token hết hạn.</li>
 * <li>must_change_password → thêm quyền {@link #PASSWORD_CHANGE_REQUIRED}; SecurityConfig chỉ cho gọi đổi mật khẩu và /me.</li>
 * <li>Claim "tv" khác accounts.token_version → 401: token đã bị thu hồi do đổi/đặt lại mật khẩu hoặc đăng xuất.
 * Token phát trước khi có cột này không có claim "tv", được coi là phiên bản 0.</li>
 * </ul>
 */
@Component
public class AccountStateJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private final CredentialQueryRepository accounts;

    public AccountStateJwtConverter(CredentialQueryRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long employeeId;
        try {
            employeeId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Token không hợp lệ");
        }
        AccountState s = accounts.findAccountState(employeeId).orElse(null);
        if (s == null || !"ACTIVE".equals(s.accountStatus()) || !"ACTIVE".equals(s.workStatus()) || s.roleCodes() == null) {
            throw new InvalidBearerTokenException("Tài khoản không còn hoạt động");
        }
        if (tokenVersionOf(jwt) != s.tokenVersion()) {
            throw new InvalidBearerTokenException("Phiên đăng nhập đã hết hiệu lực, hãy đăng nhập lại");
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : s.roleCodes().split(",")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        if (s.mustChangePassword()) {
            authorities.add(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static long tokenVersionOf(Jwt jwt) {
        Object tv = jwt.getClaims().get(JwtTokenService.TOKEN_VERSION_CLAIM);
        if (tv == null) {
            return 0;
        }
        if (tv instanceof Number n) {
            return n.longValue();
        }
        throw new InvalidBearerTokenException("Token không hợp lệ");
    }
}
