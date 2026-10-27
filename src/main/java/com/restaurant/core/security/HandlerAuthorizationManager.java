package com.restaurant.core.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Kiểm quyền ở tầng filter bằng chính biểu thức @PreAuthorize của method controller sẽ xử lý request.
 * <p>
 * Lý do: @PreAuthorize trên method chỉ chạy SAU khi Spring MVC đọc + validate body, nên người không có quyền gửi body sai
 * nhận 400 (lộ tên trường) thay vì 403. Ở đây quyền được kiểm TRƯỚC, còn nguồn khai báo quyền vẫn duy nhất là annotation
 * trên controller (@PreAuthorize ở method vẫn chạy như lớp thứ hai). Tài khoản phải đổi mật khẩu bị chặn trước tiên.
 */
@Component
public class HandlerAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);
    private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);
    /** Method không có @PreAuthorize: chỉ cần đã đăng nhập. */
    private static final WebExpressionAuthorizationManager AUTHENTICATED = new WebExpressionAuthorizationManager("isAuthenticated()");

    private final ApplicationContext context;
    private final Map<HandlerMethod, WebExpressionAuthorizationManager> cache = new ConcurrentHashMap<>();
    /**
     * Lấy bean thật lúc dùng lần đầu (SecurityFilterChain tạo trước Spring MVC). Không dùng @Lazy: proxy CGLIB không
     * chuyển tiếp được getHandler() vì đó là method final.
     */
    private volatile RequestMappingHandlerMapping handlerMapping;

    public HandlerAuthorizationManager(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        if (!SecurityConfig.passwordChanged(authentication.get())) {
            return DENIED;
        }
        HandlerMethod handler = handlerOf(context.getRequest());
        if (handler == null) {
            return GRANTED;                       // không có endpoint → để MVC trả 404
        }
        return cache.computeIfAbsent(handler, HandlerAuthorizationManager::expressionOf).authorize(authentication, context);
    }

    /** Tra handler như DispatcherServlet sẽ làm; đường dẫn phải được parse sẵn (Spring MVC dùng PathPattern). */
    private HandlerMethod handlerOf(HttpServletRequest request) {
        Object previous = request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
        ServletRequestPathUtils.parseAndCache(request);
        try {
            HandlerExecutionChain chain = mapping().getHandler(request);
            return chain != null && chain.getHandler() instanceof HandlerMethod hm ? hm : null;
        } catch (Exception e) {
            return null;                          // ví dụ sai method HTTP → MVC tự trả lỗi; @PreAuthorize vẫn là lớp thứ hai
        } finally {
            if (previous == null) {
                request.removeAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
            } else {
                request.setAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE, previous);
            }
        }
    }

    private RequestMappingHandlerMapping mapping() {
        RequestMappingHandlerMapping m = handlerMapping;
        if (m == null) {
            m = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            handlerMapping = m;
        }
        return m;
    }

    private static WebExpressionAuthorizationManager expressionOf(HandlerMethod handler) {
        PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
        if (rule == null) {
            rule = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
        }
        return rule == null ? AUTHENTICATED : new WebExpressionAuthorizationManager(rule.value());
    }
}
