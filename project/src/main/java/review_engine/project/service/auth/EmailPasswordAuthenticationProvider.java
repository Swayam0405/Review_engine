package review_engine.project.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import review_engine.project.entity.AuthIdentity;
import review_engine.project.entity.User;
import review_engine.project.enums.AccountStatus;
import review_engine.project.enums.AuthProvider;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.AuthIdentityRepository;
import review_engine.project.util.EmailNormalizer;

/**
 * Authenticates email/password identities and enforces verification before login.
 */
@Component
public class EmailPasswordAuthenticationProvider implements AuthenticationProvider {
    private final AuthIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;

    public EmailPasswordAuthenticationProvider(AuthIdentityRepository identityRepository,
                                               PasswordEncoder passwordEncoder) {
        this.identityRepository = identityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.EMAIL_PASSWORD;
    }

    /**
     * Verifies the submitted password and checks account and email-identity status.
     *
     * @param subject account email
     * @param credential submitted password
     * @return authenticated application user
     * @throws ApiException for invalid, unverified, or disabled accounts
     */
    @Override
    public User authenticate(String subject, String credential) {
        String email = EmailNormalizer.normalize(subject);
        AuthIdentity identity = identityRepository
                .findByProviderAndProviderSubject(AuthProvider.EMAIL_PASSWORD, email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        if (!passwordEncoder.matches(credential, identity.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        if (identity.getVerifiedAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Email verification is required before login.");
        }
        User user = identity.getUser();
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is disabled.");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Email verification is required before login.");
        }
        return user;
    }
}
