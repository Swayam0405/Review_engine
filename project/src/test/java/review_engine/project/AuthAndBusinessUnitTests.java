package review_engine.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
import review_engine.project.config.SecurityConfig;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import review_engine.project.entity.AuthIdentity;
import review_engine.project.entity.Business;
import review_engine.project.entity.RefreshToken;
import review_engine.project.entity.User;
import review_engine.project.enums.AuthProvider;
import review_engine.project.exception.ApiException;
import review_engine.project.service.auth.provider.GoogleAuthenticationProvider;
import review_engine.project.service.auth.provider.GoogleIdentityVerifier;
import review_engine.project.service.auth.provider.VerifiedGoogleIdentity;
import review_engine.project.repository.AuthIdentityRepository;
import review_engine.project.repository.BusinessRepository;
import review_engine.project.repository.RefreshTokenRepository;
import review_engine.project.repository.UserRepository;
import review_engine.project.service.BusinessService;
import review_engine.project.service.auth.EmailPasswordAuthenticationProvider;
import review_engine.project.service.auth.OtpHasher;
import review_engine.project.service.auth.TokenService;
import review_engine.project.util.EmailNormalizer;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * Unit tests for deterministic authentication helpers, email login policy,
 * and business input validation without external services or a database.
 */
class AuthAndBusinessUnitTests {
    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void configureJwtHelpers() {
        SecurityConfig configuration = new SecurityConfig();
        var key = configuration.jwtSecretKey("unit-test-signing-key-with-at-least-thirty-two-bytes");
        jwtEncoder = configuration.jwtEncoder(key);
        jwtDecoder = configuration.jwtDecoder(key, "unit-test-issuer");
    }

    @Test
    void shouldNormalizeEmailIndependentOfLocale() {
        assertEquals("user@example.com", EmailNormalizer.normalize("  User@Example.COM  "));
    }

    @Test
    void shouldHashOtpAndCompareInConstantTime() {
        OtpHasher hasher = new OtpHasher("unit-test-secret-with-at-least-32-bytes");
        String hash = hasher.hash("user@example.com", "031924");

        assertTrue(hasher.matches("user@example.com", "031924", hash));
        assertFalse(hasher.matches("other@example.com", "031924", hash));
        assertFalse(hasher.matches("user@example.com", "031925", hash));
        assertFalse(hash.contains("031924"));
    }

    @Test
    void shouldRejectEmailLoginUntilIdentityIsVerified() {
        AuthIdentityRepository identities = mock(AuthIdentityRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        User user = new User();
        user.setId(UUID.randomUUID());
        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.EMAIL_PASSWORD);
        identity.setProviderSubject("person@example.com");
        identity.setPasswordHash("encoded");
        when(identities.findByProviderAndProviderSubject(AuthProvider.EMAIL_PASSWORD, "person@example.com"))
                .thenReturn(Optional.of(identity));
        when(encoder.matches("correct-password", "encoded")).thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () ->
                new EmailPasswordAuthenticationProvider(identities, encoder)
                        .authenticate("PERSON@example.com", "correct-password"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void shouldRejectNonHttpsGoogleReviewUrl() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        UserRepository users = mock(UserRepository.class);
        UUID ownerId = UUID.randomUUID();
        when(businesses.findByOwnerId(ownerId)).thenReturn(Optional.of(new review_engine.project.entity.Business()));

        ApiException error = assertThrows(ApiException.class, () ->
                new BusinessService(businesses, users).updateGoogleReviewUrl(ownerId, "javascript:alert(1)"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void shouldRejectInvalidBusinessTimezone() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        UserRepository users = mock(UserRepository.class);
        UUID ownerId = UUID.randomUUID();
        when(businesses.existsByOwnerId(ownerId)).thenReturn(false);
        when(users.findById(ownerId)).thenReturn(Optional.of(new User()));

        var request = new review_engine.project.dto.request.BusinessRequest(
                "Cafe", "Restaurant", "Main Street", "Not/A_Timezone", null);
        ApiException error = assertThrows(ApiException.class, () ->
                new BusinessService(businesses, users).create(ownerId, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void shouldCreateGoogleAccountFromVerifiedStableSubject() {
        GoogleIdentityVerifier verifier = mock(GoogleIdentityVerifier.class);
        AuthIdentityRepository identities = mock(AuthIdentityRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(verifier.verify("verified-token"))
                .thenReturn(new VerifiedGoogleIdentity("google-sub-123", "Owner@Example.com", "Business Owner"));
        when(identities.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(users.existsByEmail("owner@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.save(any(AuthIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = new GoogleAuthenticationProvider(verifier, identities, users)
                .authenticate(null, "verified-token");

        assertEquals("owner@example.com", user.getEmail());
        assertEquals("Business Owner", user.getName());
        assertNotNull(user.getAccountStatus());
        verify(identities).save(any(AuthIdentity.class));
    }

    @Test
    void shouldNotSilentlyLinkGoogleToExistingEmailAccount() {
        GoogleIdentityVerifier verifier = mock(GoogleIdentityVerifier.class);
        AuthIdentityRepository identities = mock(AuthIdentityRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(verifier.verify("verified-token"))
                .thenReturn(new VerifiedGoogleIdentity("google-sub-123", "Owner@Example.com", "Owner"));
        when(identities.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(users.existsByEmail("owner@example.com")).thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () ->
                new GoogleAuthenticationProvider(verifier, identities, users)
                        .authenticate(null, "verified-token"));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void shouldDeriveBusinessOwnerFromAuthenticatedUser() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        UserRepository users = mock(UserRepository.class);
        UUID ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setId(ownerId);
        when(businesses.existsByOwnerId(ownerId)).thenReturn(false);
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        when(businesses.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new review_engine.project.dto.request.BusinessRequest(
                "Cafe", "Restaurant", "Main Street", "UTC", "https://g.page/r/example/review");

        new BusinessService(businesses, users).create(ownerId, request);

        org.mockito.ArgumentCaptor<Business> saved = org.mockito.ArgumentCaptor.forClass(Business.class);
        verify(businesses).save(saved.capture());
        assertEquals(owner, saved.getValue().getOwner());
    }

    @Test
    void shouldRejectSecondBusinessForSameOwner() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        UserRepository users = mock(UserRepository.class);
        UUID ownerId = UUID.randomUUID();
        when(businesses.existsByOwnerId(ownerId)).thenReturn(true);
        var request = new review_engine.project.dto.request.BusinessRequest(
                "Cafe", "Restaurant", "Main Street", "UTC", null);

        ApiException error = assertThrows(ApiException.class, () ->
                new BusinessService(businesses, users).create(ownerId, request));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void shouldValidateJwtSignatureIssuerExpiryAndType() {
        Instant now = Instant.now();
        String valid = encodeJwt(now.plusSeconds(60), "access");

        assertEquals("access", jwtDecoder.decode(valid).getClaimAsString("token_type"));
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThrows(JwtException.class, () -> jwtDecoder.decode(String.join(".", parts)));
        assertThrows(JwtException.class, () -> jwtDecoder.decode(encodeJwt(now.minusSeconds(600), "access")));
        assertThrows(JwtException.class, () -> jwtDecoder.decode(encodeJwt(now.plusSeconds(60), "refresh")));
    }

    @Test
    void shouldRotateHashedRefreshTokenAndRevokeFamilyAfterReuse() throws Exception {
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        List<RefreshToken> persisted = new ArrayList<>();
        when(tokens.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            persisted.add(token);
            return token;
        });
        RefreshToken oldToken = new RefreshToken();
        User user = new User();
        user.setId(UUID.randomUUID());
        oldToken.setUser(user);
        String rawRefresh = "original-secret-refresh-token";
        oldToken.setTokenHash(sha256(rawRefresh));
        oldToken.setExpiresAt(Instant.now().plusSeconds(120));
        when(tokens.findByTokenHashForUpdate(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            if (oldToken.getTokenHash().equals(hash)) {
                return Optional.of(oldToken);
            }
            return persisted.stream().filter(token -> hash.equals(token.getTokenHash())).findFirst();
        });
        when(tokens.findAllByUserIdAndRevokedAtIsNull(user.getId())).thenAnswer(invocation -> persisted);

        TokenService service = new TokenService(jwtEncoder, tokens, "unit-test-issuer", 15, 30);
        TokenService.RefreshResult result = service.rotate(rawRefresh);

        assertEquals(TokenService.RefreshFailure.NONE, result.failure());
        assertNotNull(oldToken.getRevokedAt());
        assertNotNull(oldToken.getReplacedBy());
        assertFalse(rawRefresh.equals(oldToken.getTokenHash()));
        assertEquals(64, oldToken.getTokenHash().length());

        TokenService.RefreshResult replay = service.rotate(rawRefresh);
        assertEquals(TokenService.RefreshFailure.REUSE_DETECTED, replay.failure());
        assertNotNull(persisted.getFirst().getRevokedAt());
    }

    private String encodeJwt(Instant expiresAt, String tokenType) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("unit-test-issuer")
                .subject(UUID.randomUUID().toString())
                .issuedAt(expiresAt.isAfter(now) ? now : expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .claim("token_type", tokenType)
                .build();
        Jwt encoded = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return encoded.getTokenValue();
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
