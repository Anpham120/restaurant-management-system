package vn.khoibep.rms.config;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import vn.khoibep.rms.entity.Employee;
import vn.khoibep.rms.enums.Role;
import vn.khoibep.rms.repository.EmployeeRepository;

/**
 * BR-48: the first admin of a new database without demo accounts, as on production, from APP_INITIAL_ADMIN_PASSWORD.
 * Once anyone exists the setting does nothing, so it can be removed from the server after the first sign-in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminInitializer implements ApplicationRunner {

    /** Longer than the 6 of BR-01: the account faces the Internet before anyone changes its password. */
    static final int MIN_PASSWORD = 12;

    private final AppProperties props;
    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (props.demoAccounts().enabled() || employees.count() > 0) {
            return;
        }
        String password = props.initialAdmin().password();
        if (password == null || password.isBlank()) {
            log.warn("No employee yet: set APP_INITIAL_ADMIN_PASSWORD and restart to create the first admin.");
            return;
        }
        if (password.length() < MIN_PASSWORD) {
            throw new IllegalStateException(
                    "APP_INITIAL_ADMIN_PASSWORD needs at least " + MIN_PASSWORD + " characters");
        }
        String username = props.initialAdmin().username().trim().toLowerCase(Locale.ROOT);
        employees.save(new Employee("Quản trị hệ thống", username, passwordEncoder.encode(password), Role.ADMIN));
        log.warn("Created the first admin '{}'. Sign in, change the password, then remove APP_INITIAL_ADMIN_PASSWORD.",
                username);
    }
}
