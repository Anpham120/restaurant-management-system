package vn.bnn.rms.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import vn.bnn.rms.common.exception.ApiException;
import vn.bnn.rms.employee.enums.Role;

/** The signed-in employee. Role checks follow the role hierarchy (ADMIN > MANAGER > WAITER, CHEF, CASHIER). */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final RoleHierarchy roleHierarchy;

    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken)) {
            throw ApiException.unauthorized("Chưa đăng nhập");
        }
        return Long.valueOf(auth.getName());
    }

    public boolean hasRole(Role role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String wanted = "ROLE_" + role.name();
        return roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities()).stream()
                .anyMatch(a -> wanted.equals(a.getAuthority()));
    }

    public void require(Role role, String message) {
        if (!hasRole(role)) {
            throw ApiException.forbidden(message);
        }
    }
}
