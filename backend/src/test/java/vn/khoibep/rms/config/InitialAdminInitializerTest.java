package vn.khoibep.rms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.khoibep.rms.entity.Employee;
import vn.khoibep.rms.enums.Role;
import vn.khoibep.rms.repository.EmployeeRepository;

/** BR-48, US-02 AC4: the first admin of a production database, from APP_INITIAL_ADMIN_PASSWORD. */
class InitialAdminInitializerTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void anEmptyDatabaseGetsItsFirstAdmin() {
        when(employees.count()).thenReturn(0L);

        initializer(false, " Admin ", "a-long-password-1").run(null);

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getUsername()).isEqualTo("admin");
        assertThat(encoder.matches("a-long-password-1", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void nothingIsCreatedOnceAnyoneExists() {
        when(employees.count()).thenReturn(3L);

        initializer(false, "admin", "a-long-password-1").run(null);

        verify(employees, never()).save(any());
    }

    @Test
    void demoAccountsTakeItsPlace() {
        when(employees.count()).thenReturn(0L);

        initializer(true, "admin", "a-long-password-1").run(null);

        verify(employees, never()).save(any());
    }

    @Test
    void withoutAPasswordItOnlyWarns() {
        when(employees.count()).thenReturn(0L);

        initializer(false, "admin", "").run(null);

        verify(employees, never()).save(any());
    }

    @Test
    void aShortPasswordStopsTheStart() {
        when(employees.count()).thenReturn(0L);

        assertThatThrownBy(() -> initializer(false, "admin", "123456").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12 characters");
        verify(employees, never()).save(any());
    }

    private InitialAdminInitializer initializer(boolean demo, String username, String password) {
        AppProperties props = new AppProperties("Asia/Ho_Chi_Minh", null, null, null, List.of(),
                new AppProperties.DemoAccounts(demo, "123456"), new AppProperties.InitialAdmin(username, password),
                null);
        return new InitialAdminInitializer(props, employees, encoder);
    }
}
