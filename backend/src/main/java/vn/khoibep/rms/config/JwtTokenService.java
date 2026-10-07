package vn.khoibep.rms.config;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import vn.khoibep.rms.entity.Employee;

/**
 * Issues the HS256 access token (BR-01: valid for app.jwt.ttl, 12 hours by default). It carries the token version of
 * the account, so counting that up revokes it (BR-41).
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    /** The token version of the account when the token was issued (BR-41). */
    public static final String VERSION_CLAIM = "ver";

    private final JwtEncoder encoder;
    private final AppProperties props;

    public String issue(Employee employee) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("khoibep-rms")
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().ttl()))
                .subject(String.valueOf(employee.getId()))
                .claim("name", employee.getFullName())
                .claim("role", employee.getRole().name())
                .claim(VERSION_CLAIM, employee.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
