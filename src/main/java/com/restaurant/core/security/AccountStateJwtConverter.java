package com.restaurant.core.security;

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

import com.restaurant.core.employee.repository.AccountRepository;
import com.restaurant.core.employee.repository.AccountRepository.AccountState;

/**
 * Chữ ký JWT hợp lệ chưa đủ: mỗi request đọc lại trạng thái tài khoản trong DB (một câu SELECT).
 * <ul>
 * <li>Tài khoản bị khóa / nhân viên nghỉ việc / không còn vai trò → 401 ngay, kể cả với các API ghi trực tiếp mà
 * rc_authorize không bảo vệ (nhân viên, thực đơn, danh mục kho…).</li>
 * <li>Vai trò lấy từ DB, không từ claim "roles" → đổi vai trò có hiệu lực ngay, không chờ token hết hạn.</li>
 * <li>must_change_password → thêm quyền {@link #PASSWORD_CHANGE_REQUIRED}; SecurityConfig chỉ cho gọi đổi mật khẩu và /me.</li>
 * </ul>
 */
@Component
public class AccountStateJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private final AccountRepository accounts;

    public AccountStateJwtConverter(AccountRepository accounts) {
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
        AccountState s = accounts.findState(employeeId).orElse(null);
        if (s == null || !"ACTIVE".equals(s.accountStatus()) || !"ACTIVE".equals(s.workStatus()) || s.roleCodes() == null) {
            throw new InvalidBearerTokenException("Tài khoản không còn hoạt động");
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
}
