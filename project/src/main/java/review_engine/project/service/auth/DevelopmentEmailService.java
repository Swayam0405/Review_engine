package review_engine.project.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import review_engine.project.exception.ApiException;

/**
 * Development-only mail adapter for local OTP testing without an external provider.
 * The matching code is returned only by development-profile API responses when enabled.
 */
@Service
@Primary
@Profile("dev")
public class DevelopmentEmailService implements EmailService {
    private final boolean developmentOtpEnabled;

    public DevelopmentEmailService(@Value("${app.email.dev-return-otp:false}") boolean developmentOtpEnabled) {
        this.developmentOtpEnabled = developmentOtpEnabled;
    }

    /**
     * Accepts OTP delivery only when the explicit development response switch is enabled.
     *
     * @param email destination address (not retained)
     * @param code code returned in the development-only API response (not logged or retained)
     * @throws ApiException if local OTP response mode is not explicitly enabled
     */
    @Override
    public void sendVerificationCode(String email, String code) {
        if (!developmentOtpEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Development email delivery requires DEV_RETURN_OTP=true.");
        }
    }
}
