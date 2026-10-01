package vn.khoibep.rms.employee.config;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import vn.khoibep.rms.config.AppProperties;
import vn.khoibep.rms.employee.entity.Employee;
import vn.khoibep.rms.employee.enums.PayType;
import vn.khoibep.rms.employee.enums.Role;
import vn.khoibep.rms.employee.repository.EmployeeRepository;

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
        // Made-up pay for the demo: the repository is public, so no real figures (BR-22).
        employees.saveAll(List.of(
                new Employee("Quản trị hệ thống", "admin", hash, Role.ADMIN),
                paid(new Employee("Nguyễn Văn Quản", "quanly", hash, Role.MANAGER), PayType.MONTHLY, 12_000_000),
                paid(new Employee("Trần Thị Phục", "phucvu", hash, Role.WAITER), PayType.HOURLY, 25_000),
                paid(new Employee("Lê Văn Bếp", "bep", hash, Role.CHEF), PayType.MONTHLY, 10_000_000),
                paid(new Employee("Phạm Thị Ngân", "thungan", hash, Role.CASHIER), PayType.HOURLY, 25_000)));
        log.warn("Created demo accounts admin, quanly, phucvu, bep, thungan. Turn off app.demo-accounts.enabled in production.");
    }

    private static Employee paid(Employee employee, PayType payType, long payRate) {
        employee.setPayType(payType);
        employee.setPayRate(payRate);
        return employee;
    }
}
