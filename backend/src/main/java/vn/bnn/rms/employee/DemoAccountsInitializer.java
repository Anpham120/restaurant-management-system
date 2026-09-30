package vn.bnn.rms.employee;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import vn.bnn.rms.config.AppProperties;

/** Creates one demo account per role on an empty database when app.demo-accounts.enabled=true. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoAccountsInitializer implements ApplicationRunner {

    private final AppProperties props;
    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.demoAccounts().enabled() || employees.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(props.demoAccounts().password());
        employees.saveAll(List.of(
                new Employee("Quản trị hệ thống", "admin", hash, Role.ADMIN),
                new Employee("Nguyễn Văn Quản", "quanly", hash, Role.MANAGER),
                new Employee("Trần Thị Phục", "phucvu", hash, Role.WAITER),
                new Employee("Lê Văn Bếp", "bep", hash, Role.CHEF),
                new Employee("Phạm Thị Ngân", "thungan", hash, Role.CASHIER)));
        log.warn("Created demo accounts admin, quanly, phucvu, bep, thungan. Turn off app.demo-accounts.enabled in production.");
    }
}
