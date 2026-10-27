package com.restaurant.core.security;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Danh tính đã xác thực của request hiện tại. {@link #employeeId()} là giá trị DUY NHẤT được truyền vào tham số
 * p_actor của các hàm rc_* — không bao giờ nhận actor/employeeId từ body hay query của client.
 */
@Component
public class CurrentUser {

    public long employeeId() {
        return Long.parseLong(jwt().getSubject());
    }

    public String username() {
        return jwt().getClaimAsString("username");
    }

    /** Vai trò hiện hành trong DB (AccountStateJwtConverter), không phải claim lúc phát token. */
    public List<String> roles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return List.of();
        }
        return auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5)).sorted().toList();
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("Không có người dùng đã xác thực trong request này");
    }
}
