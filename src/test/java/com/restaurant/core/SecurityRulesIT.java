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
 * Mọi endpoint: vai trò không được phép nhận 403 FORBIDDEN ngay cả khi gửi body sai (quyền kiểm trước validate, không lộ
 * tên trường); vai trò được phép không bị chặn ở tầng quyền. Vai trò được phép đọc từ @PreAuthorize của chính controller.
 */
class SecurityRulesIT extends OrderApiTestBase {

    private static final Set<String> PUBLIC = Set.of("/api/auth/login", "/api/webhooks/sepay");
    private static final Pattern ROLE = Pattern.compile("'(\\w+)'");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyEndpointChecksRoleBeforeReadingBody() throws Exception {
        Map<String, String> tokens = Map.of("MANAGER", manager, "WAITER", waiter, "KITCHEN", kitchen, "CASHIER", cashier);
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            Set<String> allowed = allowedRoles(e.getValue());
            for (String pattern : e.getKey().getPatternValues()) {
                if (PUBLIC.contains(pattern) || !pattern.startsWith("/api/")) {
                    continue;
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
                        boolean forbidden = r.getResponse().getStatus() == 403 && "FORBIDDEN".equals(code);
                        if (allowed.contains(role.getKey()) == forbidden) {
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
