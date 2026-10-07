package vn.khoibep.rms.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.stereotype.Service;

/** NFR-11: a service call at or past the threshold is logged as WARN; the result and any error pass through. */
class LoggingAspectTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void listen() {
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void stopListening() {
        logger.detachAppender(logged);
    }

    @Test
    void aSlowCallIsLoggedAsWarnWithItsClassAndMethod() {
        Kitchen kitchen = advised(new Kitchen(), Duration.ZERO);

        assertThat(kitchen.cook("phở")).isEqualTo("phở xong");

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).startsWith("Slow service call Kitchen.cook took ");
        });
    }

    @Test
    void aFastCallIsNotWarnedAbout() {
        Kitchen kitchen = advised(new Kitchen(), Duration.ofMinutes(1));

        assertThat(kitchen.cook("phở")).isEqualTo("phở xong");

        assertThat(logged.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }

    @Test
    void anErrorPassesThroughUnchangedAndIsStillTimed() {
        Kitchen kitchen = advised(new Kitchen(), Duration.ZERO);

        assertThatThrownBy(kitchen::burn).isInstanceOf(IllegalStateException.class).hasMessage("cháy");

        assertThat(logged.list).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).startsWith("Slow service call Kitchen.burn"));
    }

    @Test
    void onlyServicesAreTimed() {
        Helper helper = advised(new Helper(), Duration.ZERO);

        assertThat(helper.work()).isEqualTo("done");

        assertThat(logged.list).isEmpty();
    }

    private static <T> T advised(T target, Duration threshold) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new LoggingAspect(threshold));
        return factory.getProxy();
    }

    /** Stands in for a real service: the aspect picks classes by their {@code @Service} annotation. */
    @Service
    static class Kitchen {

        public String cook(String dish) {
            return dish + " xong";
        }

        public void burn() {
            throw new IllegalStateException("cháy");
        }
    }

    static class Helper {

        public String work() {
            return "done";
        }
    }
}
