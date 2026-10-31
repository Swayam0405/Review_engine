package review_engine.project.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;
import review_engine.project.dto.response.ApiResponse;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Configures stateless JWT authentication, narrowly scoped cookie CSRF protection,
 * explicit CORS origins, and safe authentication/authorization failures.
 */
@Configuration
public class SecurityConfig {
    /**
     * Configures BCrypt work factor 12 for application passwords.
     *
     * @return configured password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Creates the HMAC signing key after enforcing a safe minimum key length.
     *
     * @param secret secret material supplied through JWT_SECRET
     * @return HMAC-SHA-256 key
     */
    @Bean
    public SecretKey jwtSecretKey(@Value("${app.security.jwt.secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    /**
     * Creates the encoder used to sign application access tokens.
     *
     * @param jwtSecretKey HMAC signing key
     * @return Nimbus JWT encoder
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey));
    }

    /**
     * Creates a decoder that validates signature, issuer, standard timestamps, and token type.
     *
     * @param jwtSecretKey HMAC signing key
     * @param issuer required token issuer
     * @return validated JWT decoder
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, @Value("${app.security.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> typeValidator = new JwtClaimValidator<>("token_type", "access"::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, typeValidator));
        return decoder;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${app.security.cookie.secure}") boolean secure,
                                            @Value("${app.security.cookie.same-site}") String sameSite) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/")
                .secure(secure)
                .sameSite(sameSite)
                .httpOnly(false));
        return repository;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins}") String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN",
                "X-Internal-Api-Key", "X-Webhook-Secret"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectMapper objectMapper,
                                            CsrfTokenRepository csrfTokenRepository) throws Exception {
        RequestMatcher cookieAuthenticatedOperations = request ->
                HttpMethod.POST.matches(request.getMethod())
                        && ("/api/auth/refresh".equals(request.getServletPath())
                        || "/api/auth/logout".equals(request.getServletPath()));

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(cookieAuthenticatedOperations))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-otp",
                                "/api/auth/login", "/api/auth/google", "/api/auth/refresh", "/api/auth/logout",
                                "/api/public/feedback/**", "/api/internal/webhooks/whatsapp/delivery",
                                "/api/internal/feedback-requests/*/delivery",
                                "/actuator/health")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, objectMapper, HttpStatus.FORBIDDEN,
                                        "Access to this resource is denied."))
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, objectMapper, HttpStatus.FORBIDDEN,
                                        "Access to this resource is denied.")));
        return http.build();
    }

    private void writeSecurityError(HttpServletResponse response, ObjectMapper objectMapper,
                                    HttpStatus status, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(message));
    }
}
