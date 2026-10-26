package com.restaurant.core.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aspect nền tảng DUY NHẤT: đo thời gian mọi phương thức public của các bean trong package {@code ..service..}.
 * Chỉ ghi tên class/method và thời gian — KHÔNG ghi tham số hay kết quả (có thể chứa mật khẩu, JWT, tiền).
 * Không đổi kết quả trả về, ném lại nguyên exception; không mở/đóng transaction.
 * Mức log: DEBUG bình thường, WARN khi chậm hơn ngưỡng {@code app.logging.slow-service-ms}, WARN khi lỗi.
 */
@Aspect
@Component
public class PerformanceLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceLoggingAspect.class);

    private final long slowThresholdMs;

    public PerformanceLoggingAspect(@Value("${app.logging.slow-service-ms:500}") long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    @Around("within(com.restaurant.core..service..*) && execution(public * *(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getDeclaringType().getSimpleName() + "." + pjp.getSignature().getName();
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms >= slowThresholdMs) {
                log.warn("{} chậm: {} ms", name, ms);
            } else {
                log.debug("{} {} ms", name, ms);
            }
            return result;
        } catch (Throwable ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.warn("{} lỗi sau {} ms: {}", name, ms, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
