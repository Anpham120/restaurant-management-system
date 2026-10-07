package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJPrecedenceInformation;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;

import vn.khoibep.rms.IntegrationTest;
import vn.khoibep.rms.service.OrderService;
import vn.khoibep.rms.service.SettingsService;

/** P3-06: the logging aspect wraps the application's real services, not only the stand-in of LoggingAspectTest. */
class LoggingAspectIntegrationTest extends IntegrationTest {

    @Autowired
    private OrderService orders;

    @Autowired
    private SettingsService settings;

    @Test
    void servicesAreAdvisedByTheLoggingAspect() {
        for (Object service : new Object[] {orders, settings}) {
            assertThat(AopUtils.isAopProxy(service)).isTrue();
            assertThat(((Advised) service).getAdvisors())
                    .anyMatch(advisor -> advisor instanceof AspectJPrecedenceInformation aspect
                            && "loggingAspect".equals(aspect.getAspectName()));
        }
    }
}
