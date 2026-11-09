package com.restaurant.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.jayway.jsonpath.JsonPath;
import com.restaurant.core.order.OrderApiTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Mọi endpoint /api (trừ endpoint công khai và hai endpoint chỉ cần đăng nhập) phải có @PreAuthorize. Vai trò được phép
 * (đọc từ chính @PreAuthorize) không bị chặn 403; vai trò không được phép nhận 403 FORBIDDEN, hoặc 400 VALIDATION_ERROR khi
 * body sai (chuẩn Spring: @Valid chạy trước @PreAuthorize của method), không bao giờ vào tới nghiệp vụ (2xx, 404, 409).
 */
class SecurityRulesIT extends OrderApiTestBase {

    private static final Set<String> PUBLIC = Set.of("/api/auth/login", "/api/webhooks/sepay");
    /** Chỉ cần đăng nhập, khai trong SecurityConfig (tài khoản phải đổi mật khẩu vẫn gọi được). */
    private static final Set<String> AUTHENTICATED_ONLY = Set.of("/api/auth/change-password", "/api/auth/me");
    private static final Pattern ROLE = Pattern.compile("'(\\w+)'");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyEndpointEnforcesItsRoles() throws Exception {
        Map<String, String> tokens = Map.of("MANAGER", manager, "WAITER", waiter, "KITCHEN", kitchen, "CASHIER", cashier);
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            Set<String> allowed = allowedRoles(e.getValue());
            for (String pattern : e.getKey().getPatternValues()) {
                if (PUBLIC.contains(pattern) || !pattern.startsWith("/api/")) {
                    continue;
                }
                if (!AUTHENTICATED_ONLY.contains(pattern) && !hasPreAuthorize(e.getValue())) {
                    violations.add(pattern + " thiếu @PreAuthorize");
                }
                String url = pattern.replace("{kind}", "RAW").replace("{businessDate}", "2026-01-01").replaceAll("\\{[^}]+}", "999999999");
                for (var m : e.getKey().getMethodsCondition().getMethods()) {
                    for (Map.Entry<String, String> role : tokens.entrySet()) {
                        var req = request(HttpMethod.valueOf(m.name()), url).header("Authorization", role.getValue());
                        if (m.name().equals("POST") || m.name().equals("PUT")) {
                            req.contentType(MediaType.APPLICATION_JSON).content("{}");
                        }
                        MvcResult r = mvc.perform(req).andReturn();
                        String body = r.getResponse().getContentAsString();
                        Map<String, Object> error = body.isEmpty() ? null : JsonPath.read(body, "$.error");
                        String code = error == null ? null : (String) error.get("code");
                        int status = r.getResponse().getStatus();
                        boolean forbidden = status == 403 && "FORBIDDEN".equals(code);
                        boolean rejectedBody = status == 400 && "VALIDATION_ERROR".equals(code);
                        boolean ok = allowed.contains(role.getKey()) ? !forbidden : forbidden || rejectedBody;
                        if (!ok) {
                            violations.add(m + " " + pattern + " as " + role.getKey() + " → " + r.getResponse().getStatus() + " " + code);
                        }
                        checked++;
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
        assertThat(checked).isGreaterThan(400);
    }

    private static boolean hasPreAuthorize(HandlerMethod h) {
        return AnnotatedElementUtils.findMergedAnnotation(h.getMethod(), PreAuthorize.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(h.getBeanType(), PreAuthorize.class) != null;
    }

    /** "hasAnyRole('A', 'B')" → {A, B}; không có annotation hoặc isAuthenticated() → mọi vai trò. */
    private static Set<String> allowedRoles(HandlerMethod h) {
        PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(h.getMethod(), PreAuthorize.class);
        if (rule == null) {
            rule = AnnotatedElementUtils.findMergedAnnotation(h.getBeanType(), PreAuthorize.class);
        }
        if (rule == null || rule.value().contains("isAuthenticated")) {
            return Set.of("MANAGER", "WAITER", "KITCHEN", "CASHIER");
        }
        Set<String> roles = new java.util.HashSet<>();
        Matcher mt = ROLE.matcher(rule.value());
        while (mt.find()) {
            roles.add(mt.group(1));
        }
        return roles;
    }
}
