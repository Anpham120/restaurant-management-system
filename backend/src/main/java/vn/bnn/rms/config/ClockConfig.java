package vn.bnn.rms.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The current time in the restaurant's zone, for rules that depend on it (clock-in windows, "today"). */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(AppProperties props) {
        return Clock.system(props.zoneId());
    }
}
