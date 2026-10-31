package review_engine.project.service.auth;

/**
 * Port for delivering account-verification messages through a configured provider.
 */
public interface EmailService {
    void sendVerificationCode(String email, String code);
}
