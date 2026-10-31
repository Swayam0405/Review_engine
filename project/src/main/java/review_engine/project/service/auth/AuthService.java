package review_engine.project.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.request.RegisterRequest;
import review_engine.project.dto.response.CurrentUserResponse;
import review_engine.project.dto.response.RegisterResponse;
import review_engine.project.entity.AuthIdentity;
import review_engine.project.entity.EmailVerificationOtp;
import review_engine.project.entity.User;
import review_engine.project.enums.AccountStatus;
import review_engine.project.enums.AuthProvider;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.AuthIdentityRepository;
import review_engine.project.repository.BusinessRepository;
import review_engine.project.repository.EmailVerificationOtpRepository;
import review_engine.project.repository.UserRepository;
import review_engine.project.util.EmailNormalizer;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coordinates account registration, email verification, provider authentication,
 * and server-derived current-user onboarding state.
 */
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final AuthIdentityRepository identityRepository;
    private final EmailVerificationOtpRepository otpRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpHasher otpHasher;
    private final EmailService emailService;
    private final Map<AuthProvider, AuthenticationProvider> providers;
    private final Environment environment;
    private final Duration otpLifetime;
    private final Duration resendCooldown;
    private final int maximumAttempts;
    private final boolean developmentOtpEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       AuthIdentityRepository identityRepository,
                       EmailVerificationOtpRepository otpRepository,
                       BusinessRepository businessRepository,
                       PasswordEncoder passwordEncoder,
                       OtpHasher otpHasher,
                       EmailService emailService,
                       List<AuthenticationProvider> providers,
                       Environment environment,
                       @Value("${app.security.otp.expiry-minutes}") long otpExpiryMinutes,
                       @Value("${app.security.otp.resend-cooldown-seconds}") long resendCooldownSeconds,
                       @Value("${app.security.otp.max-attempts}") int maximumAttempts,
                       @Value("${app.email.dev-return-otp:false}") boolean developmentOtpEnabled) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.otpRepository = otpRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpHasher = otpHasher;
        this.emailService = emailService;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                AuthenticationProvider::provider, Function.identity()));
        this.environment = environment;
        this.otpLifetime = Duration.ofMinutes(otpExpiryMinutes);
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
        this.maximumAttempts = maximumAttempts;
        this.developmentOtpEnabled = developmentOtpEnabled;
        if (otpExpiryMinutes <= 0 || resendCooldownSeconds <= 0 || maximumAttempts <= 0) {
            throw new IllegalArgumentException("OTP lifetime, cooldown, and attempt limit must be positive.");
        }
    }

    /**
     * Creates a pending account, a password identity, and a hashed verification challenge.
     * No application tokens or business profile are created before verification.
     *
     * @param request validated name, email, and password
     * @return safe verification-required response
     * @throws ApiException if the normalized email already belongs to an account
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account already uses this email.");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        userRepository.save(user);

        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.EMAIL_PASSWORD);
        identity.setProviderSubject(email);
        identity.setPasswordHash(passwordEncoder.encode(request.password()));
        identityRepository.save(identity);

        String code = createOtp(identity);
        emailService.sendVerificationCode(email, code);
        String responseCode = developmentOtpEnabled && environment.acceptsProfiles(Profiles.of("dev"))
                ? code : null;
        return new RegisterResponse(true, responseCode);
    }

    /**
     * Verifies the active one-time code and activates the pending account.
     * Failed-attempt counters are committed before the outcome is returned.
     *
     * @param email submitted account email
     * @param code submitted six-digit code
     * @return persisted verification result
     */
    @Transactional
    public OtpVerificationResult verifyEmail(String email, String code) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        AuthIdentity identity = identityRepository
                .findByProviderAndProviderSubjectForUpdate(AuthProvider.EMAIL_PASSWORD, normalizedEmail)
                .orElse(null);
        if (identity == null) {
            return OtpVerificationResult.INVALID;
        }
        List<EmailVerificationOtp> activeOtps = otpRepository.findActiveForUpdate(identity.getId());
        if (activeOtps.isEmpty()) {
            return OtpVerificationResult.INVALID;
        }

        EmailVerificationOtp otp = activeOtps.getFirst();
        Instant now = Instant.now();
        if (!otp.getExpiresAt().isAfter(now)) {
            otp.setConsumedAt(now);
            return OtpVerificationResult.EXPIRED;
        }
        if (otp.getAttempts() >= maximumAttempts) {
            otp.setConsumedAt(now);
            return OtpVerificationResult.ATTEMPTS_EXCEEDED;
        }
        if (!otpHasher.matches(normalizedEmail, code, otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= maximumAttempts) {
                otp.setConsumedAt(now);
                return OtpVerificationResult.ATTEMPTS_EXCEEDED;
            }
            return OtpVerificationResult.INVALID;
        }

        // Consume while holding the row lock so concurrent requests cannot redeem this code twice.
        otp.setVerifiedAt(now);
        otp.setConsumedAt(now);
        identity.setVerifiedAt(now);
        identity.getUser().setAccountStatus(AccountStatus.ACTIVE);
        return OtpVerificationResult.VERIFIED;
    }

    /**
     * Resends a code when the account is pending and outside its cooldown window.
     * Unknown and already verified addresses receive the same successful response.
     *
     * @param email submitted account email
     * @return verification code only in explicitly enabled dev mode, otherwise null
     */
    @Transactional
    public String resendOtp(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        AuthIdentity identity = identityRepository
                .findByProviderAndProviderSubjectForUpdate(AuthProvider.EMAIL_PASSWORD, normalizedEmail)
                .orElse(null);
        if (identity == null || identity.getVerifiedAt() != null
                || identity.getUser().getAccountStatus() != AccountStatus.PENDING_VERIFICATION) {
            return null;
        }

        List<EmailVerificationOtp> history = otpRepository.findAllForIdentityForUpdate(identity.getId());
        Instant now = Instant.now();
        if (!history.isEmpty() && history.getFirst().getCreatedAt().plus(resendCooldown).isAfter(now)) {
            return null;
        }
        String code = createOtp(identity);
        emailService.sendVerificationCode(normalizedEmail, code);
        return developmentOtpEnabled && environment.acceptsProfiles(Profiles.of("dev")) ? code : null;
    }

    /**
     * Authenticates email/password credentials through the matching provider adapter.
     *
     * @param email account email
     * @param password submitted password
     * @return active application user
     */
    @Transactional(readOnly = true)
    public User login(String email, String password) {
        return provider(AuthProvider.EMAIL_PASSWORD).authenticate(email, password);
    }

    /**
     * Authenticates a Google identity token through Google's verifier/provider.
     *
     * @param idToken Google identity token
     * @return active or newly created application user
     */
    public User loginWithGoogle(String idToken) {
        return provider(AuthProvider.GOOGLE).authenticate(null, idToken);
    }

    /**
     * Returns only public account data and business state derived from persistence.
     *
     * @param userId authenticated principal's application UUID
     * @return current-user DTO
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required."));
        return new CurrentUserResponse(user.getId(), user.getName(), user.getEmail(), user.getAccountStatus(),
                identityRepository.existsByUserIdAndVerifiedAtIsNotNull(userId),
                businessRepository.existsByOwnerId(userId));
    }

    private String createOtp(AuthIdentity identity) {
        Instant now = Instant.now();
        otpRepository.findActiveForUpdate(identity.getId()).forEach(existing -> existing.setConsumedAt(now));
        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        EmailVerificationOtp otp = new EmailVerificationOtp();
        otp.setUser(identity.getUser());
        otp.setAuthIdentity(identity);
        otp.setOtpHash(otpHasher.hash(identity.getProviderSubject(), code));
        otp.setExpiresAt(now.plus(otpLifetime));
        otp.setAttempts(0);
        otpRepository.save(otp);
        return code;
    }

    private AuthenticationProvider provider(AuthProvider provider) {
        AuthenticationProvider authenticationProvider = providers.get(provider);
        if (authenticationProvider == null) {
            throw new IllegalStateException("No authentication adapter is registered for " + provider + ".");
        }
        return authenticationProvider;
    }
}
