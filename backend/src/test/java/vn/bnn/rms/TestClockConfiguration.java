package vn.bnn.rms;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import vn.bnn.rms.config.AppProperties;

/** Replaces the application clock so tests can pin the time (clock-in windows, payroll months). */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

    @Bean
    @Primary
    MutableClock testClock(AppProperties props) {
        return new MutableClock(props.zoneId());
    }
}
