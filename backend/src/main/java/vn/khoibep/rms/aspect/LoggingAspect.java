package vn.khoibep.rms.aspect;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * NFR-11, NFR-02: times every public method of a {@code @Service}. A call that takes app.slow-service-threshold or
 * longer (500 ms by default, the p95 goal of NFR-02) is logged as WARN with its class, method and time, so a slow
 * request seen on the dashboard (alert SlowResponses) can be traced to the service method behind it. Faster calls are
 * logged at DEBUG. The result, any exception and the transaction around the call pass through unchanged.
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    private final long thresholdNanos;

    public LoggingAspect(@Value("${app.slow-service-threshold:500ms}") Duration threshold) {
        this.thresholdNanos = threshold.toNanos();
    }

    @Around("@within(org.springframework.stereotype.Service) && execution(public * *(..))")
    public Object logSlowCalls(ProceedingJoinPoint call) throws Throwable {
        long start = System.nanoTime();
        try {
            return call.proceed();
        } finally {
            long elapsed = System.nanoTime() - start;
            if (elapsed >= thresholdNanos) {
                log.warn("Slow service call {} took {} ms", name(call), elapsed / 1_000_000);
            } else if (log.isDebugEnabled()) {
                log.debug("Service call {} took {} ms", name(call), elapsed / 1_000_000);
            }
        }
    }

    private static String name(ProceedingJoinPoint call) {
        return call.getSignature().getDeclaringType().getSimpleName() + "." + call.getSignature().getName();
    }
}
