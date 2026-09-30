package vn.bnn.rms.config;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import vn.bnn.rms.employee.Employee;
import vn.bnn.rms.employee.EmployeeRepository;

/**
 * BR-03: every request re-checks that the employee still exists and is active, so locking an account
 * takes effect at once. The role also comes from the database, not from the token.
 */
@Component
@RequiredArgsConstructor
public class ActiveEmployeeJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final EmployeeRepository employees;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Employee employee = loadActive(jwt.getSubject());
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole().name())), jwt.getSubject());
    }

    private Employee loadActive(String subject) {
        long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Invalid token subject");
        }
        return employees.findById(id)
                .filter(Employee::isActive)
                .orElseThrow(() -> new DisabledException("Account is locked or missing"));
    }
}
