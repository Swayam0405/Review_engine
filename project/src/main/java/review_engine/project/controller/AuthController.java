package review_engine.project.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.GoogleLoginRequest;
import review_engine.project.dto.request.LoginRequest;
import review_engine.project.dto.request.RegisterRequest;
import review_engine.project.dto.request.ResendOtpRequest;
import review_engine.project.dto.request.VerifyEmailRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.AuthResponse;
import review_engine.project.dto.response.CurrentUserResponse;
import review_engine.project.dto.response.RegisterResponse;
import review_engine.project.dto.response.ResendOtpResponse;
import review_engine.project.entity.User;
import review_engine.project.exception.ApiException;
import review_engine.project.service.auth.AuthCookieService;
import review_engine.project.service.auth.AuthService;
import review_engine.project.service.auth.OtpVerificationResult;
import review_engine.project.service.auth.TokenService;

import java.util.UUID;

/**
 * HTTP endpoints for registration, authentication, verification, session management,
 * and authenticated application-user details.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, TokenService tokenService, AuthCookieService cookieService) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.cookieService = cookieService;
    }

    /**
     * Registers a pending email/password account and sends its verification code.
     *
     * @param request validated account details
     * @return verification-required response without authenticated tokens
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("Check your email to verify your account.", authService.register(request));
    }

    /**
     * Verifies an email identity and activates its pending application account.
     *
     * @param request email address and one-time code
     * @return confirmation when the code was accepted
     */
    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        OtpVerificationResult result = authService.verifyEmail(request.email(), request.otp());
        if (result != OtpVerificationResult.VERIFIED) {
            String message = switch (result) {
                case EXPIRED -> "Verification code has expired.";
                case ATTEMPTS_EXCEEDED -> "Verification attempt limit exceeded.";
                default -> "Verification code is invalid.";
            };
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return ApiResponse.success("Email verified.", null);
    }

    /**
     * Requests a verification-code resend with a generic success response.
     *
     * @param request email address to verify
     * @return generic response that does not confirm whether the account exists
     */
    @PostMapping("/resend-otp")
    public ApiResponse<ResendOtpResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        String developmentOtp = authService.resendOtp(request.email());
        return ApiResponse.success("If the account is eligible, a verification code will be sent.",
                new ResendOtpResponse(developmentOtp));
    }

    /**
     * Authenticates email/password credentials and issues application tokens.
     *
     * @param request login credentials
     * @param servletRequest originating HTTP request used for CSRF cookie setup
     * @param response response receiving refresh and CSRF cookies
     * @return short-lived access token and public user state
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest servletRequest,
                                           HttpServletResponse response) {
        User user = authService.login(request.email(), request.password());
        return ApiResponse.success("Authenticated.", issueAuthResponse(user, servletRequest, response));
    }

    /**
     * Verifies a Google ID token and issues the same application credentials as password login.
     *
     * @param request Google ID token
     * @param servletRequest originating HTTP request
     * @param response response receiving refresh and CSRF cookies
     * @return short-lived access token and public user state
     */
    @PostMapping("/google")
    public ApiResponse<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request,
                                                 HttpServletRequest servletRequest,
                                                 HttpServletResponse response) {
        User user = authService.loginWithGoogle(request.idToken());
        return ApiResponse.success("Authenticated.", issueAuthResponse(user, servletRequest, response));
    }

    /**
     * Rotates the HttpOnly refresh cookie and issues a new access token.
     *
     * @param request request containing the refresh cookie
     * @param response response receiving the replacement cookie
     * @return rotated application credentials, or an authentication error on replay/expiry
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieService.readRefreshCookie(request);
        if (refreshToken == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid.");
        }
        TokenService.RefreshResult result = tokenService.rotate(refreshToken);
        if (result.failure() != TokenService.RefreshFailure.NONE) {
            cookieService.clear(request, response);
            String message = result.failure() == TokenService.RefreshFailure.REUSE_DETECTED
                    ? "Refresh token reuse was detected." : "Refresh token has expired.";
            throw new ApiException(HttpStatus.UNAUTHORIZED, message);
        }
        TokenService.TokenBundle bundle = result.bundle();
        cookieService.setRefreshCookie(bundle, response);
        CurrentUserResponse user = authService.currentUser(bundle.userId());
        return ApiResponse.success("Session refreshed.",
                new AuthResponse(bundle.accessToken(), "Bearer", user));
    }

    /**
     * Revokes the presented refresh token and clears session cookies.
     *
     * @param request request containing the refresh cookie, if present
     * @param response response receiving expired cookies
     * @return logout confirmation
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        tokenService.revoke(cookieService.readRefreshCookie(request));
        cookieService.clear(request, response);
        return ApiResponse.success("Logged out.", null);
    }

    /**
     * Returns the authenticated user's account and server-derived business onboarding state.
     *
     * @param authentication validated Spring Security JWT principal
     * @return current user's public representation
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
        return ApiResponse.success("Current user retrieved.",
                authService.currentUser(UUID.fromString(authentication.getName())));
    }

    private AuthResponse issueAuthResponse(User user, HttpServletRequest request, HttpServletResponse response) {
        TokenService.TokenBundle bundle = tokenService.issue(user);
        cookieService.setRefreshCookie(bundle, response);
        cookieService.issueCsrfCookie(request, response);
        CurrentUserResponse currentUser = authService.currentUser(user.getId());
        return new AuthResponse(bundle.accessToken(), "Bearer", currentUser);
    }
}
