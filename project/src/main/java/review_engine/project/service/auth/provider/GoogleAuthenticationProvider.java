package review_engine.project.service.auth.provider;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.entity.AuthIdentity;
import review_engine.project.entity.User;
import review_engine.project.enums.AccountStatus;
import review_engine.project.enums.AuthProvider;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.AuthIdentityRepository;
import review_engine.project.repository.UserRepository;
import review_engine.project.service.auth.AuthenticationProvider;
import review_engine.project.util.EmailNormalizer;

import java.time.Instant;

/**
 * Resolves verified Google identities into the shared application-user model.
 */
@Component
public class GoogleAuthenticationProvider implements AuthenticationProvider {
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final AuthIdentityRepository identityRepository;
    private final UserRepository userRepository;

    public GoogleAuthenticationProvider(GoogleIdentityVerifier googleIdentityVerifier,
                                        AuthIdentityRepository identityRepository,
                                        UserRepository userRepository) {
        this.googleIdentityVerifier = googleIdentityVerifier;
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    /**
     * Verifies the Google token and either authenticates or creates its application account.
     * Existing email/password accounts are never silently linked.
     *
     * @param subject unused; the verified Google subject is taken from the token
     * @param credential Google ID token
     * @return active application user
     * @throws ApiException for invalid tokens, disabled accounts, or linking conflicts
     */
    @Override
    @Transactional
    public User authenticate(String subject, String credential) {
        VerifiedGoogleIdentity verified = googleIdentityVerifier.verify(credential);
        return identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, verified.subject())
                .map(AuthIdentity::getUser)
                .map(this::requireActive)
                .orElseGet(() -> createAccount(verified));
    }

    private User createAccount(VerifiedGoogleIdentity verified) {
        String email = EmailNormalizer.normalize(verified.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "An account already uses this email; authenticate with it before linking Google.");
        }
        User user = new User();
        String displayName = verified.name() == null || verified.name().isBlank()
                ? email : verified.name().trim();
        user.setName(displayName.substring(0, Math.min(displayName.length(), 120)));
        user.setEmail(email);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);

        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.GOOGLE);
        identity.setProviderSubject(verified.subject());
        identity.setVerifiedAt(Instant.now());
        identityRepository.save(identity);
        return user;
    }

    private User requireActive(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is disabled.");
        }
        return user;
    }
}
