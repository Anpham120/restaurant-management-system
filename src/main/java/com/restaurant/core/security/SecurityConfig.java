package com.restaurant.core.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT: client gửi "Authorization: Bearer <token>". Vai trò và trạng thái tài khoản đọc lại từ DB mỗi request
 * ({@link AccountStateJwtConverter}). Phân quyền theo endpoint bằng @PreAuthorize trên controller, được kiểm ở tầng filter
 * trước khi đọc body ({@link HandlerAuthorizationManager}). 401 = thiếu/sai token
 * hoặc tài khoản không còn hoạt động, 403 = thiếu vai trò hoặc chưa đổi mật khẩu bắt buộc.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountStateJwtConverter jwtConverter,
                                            HandlerAuthorizationManager handlerAuthorization, RestSecurityErrorHandler errors) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                       // không dùng cookie session → không cần CSRF
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // webhook SePay: không có JWT, xác thực bằng API key trong SepayWebhookService
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/sepay").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Prometheus scrape qua 127.0.0.1:8081; nginx máy chủ KHÔNG proxy đường dẫn này ra ngoài
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/error").permitAll()
                        // tài khoản phải đổi mật khẩu: chỉ được đổi mật khẩu và xem /me
                        .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        // còn lại: quyền theo @PreAuthorize của controller, kiểm TRƯỚC khi đọc body
                        .anyRequest().access(handlerAuthorization))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(props)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    static boolean passwordChanged(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)
                && !requiresPasswordChange(authentication);
    }

    static boolean requiresPasswordChange(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> AccountStateJwtConverter.PASSWORD_CHANGE_REQUIRED.equals(a.getAuthority()));
    }

    private static SecretKey secretKey(JwtProperties props) {
        return new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
