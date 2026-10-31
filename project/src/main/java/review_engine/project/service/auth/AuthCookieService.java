package review_engine.project.service.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * Writes and clears refresh-token and CSRF cookies with matching security attributes.
 */
@Component
public class AuthCookieService {
    public static final String REFRESH_COOKIE = "refresh_token";

    private final CsrfTokenRepository csrfTokenRepository;
    private final boolean secure;
    private final String sameSite;

    public AuthCookieService(CsrfTokenRepository csrfTokenRepository,
                             @Value("${app.security.cookie.secure}") boolean secure,
                             @Value("${app.security.cookie.same-site}") String sameSite) {
        this.csrfTokenRepository = csrfTokenRepository;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /**
     * Adds the opaque refresh value as an HttpOnly cookie and returns its expiry.
     *
     * @param tokenBundle tokens from the shared issuer
     * @param response HTTP response to receive Set-Cookie
     */
    public void setRefreshCookie(TokenService.TokenBundle tokenBundle, HttpServletResponse response) {
        Duration maxAge = Duration.between(java.time.Instant.now(), tokenBundle.refreshExpiresAt());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, tokenBundle.refreshToken())
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Creates the readable double-submit CSRF cookie after a successful login.
     *
     * @param request originating request
     * @param response response receiving the CSRF cookie
     */
    public void issueCsrfCookie(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);
    }

    /**
     * Reads the current opaque refresh token without exposing it through an API response.
     *
     * @param request incoming request
     * @return cookie value, or null if absent
     */
    public String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> REFRESH_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Clears refresh and CSRF cookies using the original cookie paths.
     *
     * @param request originating request
     * @param response response receiving the expired cookies
     */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        csrfTokenRepository.saveToken(null, request, response);
    }
}
