package vn.khoibep.rms.config;

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

import vn.khoibep.rms.entity.Employee;
import vn.khoibep.rms.repository.EmployeeRepository;

/**
 * BR-03: every request re-checks that the employee still exists and is active, so locking an account
 * takes effect at once. The role also comes from the database, not from the token.
 * BR-41: a token issued before the account last revoked its tokens is refused, for the API and for realtime alike.
 */
@Component
@RequiredArgsConstructor
public class ActiveEmployeeJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final EmployeeRepository employees;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Employee employee = loadActive(jwt.getSubject());
        // Tokens issued before versions existed carry none, which counts as the first version.
        Number version = jwt.getClaim(JwtTokenService.VERSION_CLAIM);
        if ((version == null ? 0 : version.intValue()) != employee.getTokenVersion()) {
            throw new BadCredentialsException("Token has been revoked");
        }
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
