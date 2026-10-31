package review_engine.project.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.entity.RefreshToken;
import review_engine.project.entity.User;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues short-lived signed access tokens and rotates hashed opaque refresh tokens.
 */
@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final String issuer;
    private final Duration accessTokenLifetime;
    private final Duration refreshTokenLifetime;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder,
                        RefreshTokenRepository refreshTokenRepository,
                        @Value("${app.security.jwt.issuer}") String issuer,
                        @Value("${app.security.jwt.access-token-minutes}") long accessTokenMinutes,
                        @Value("${app.security.refresh-token-days}") long refreshTokenDays) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.issuer = issuer;
        this.accessTokenLifetime = Duration.ofMinutes(accessTokenMinutes);
        this.refreshTokenLifetime = Duration.ofDays(refreshTokenDays);
        if (accessTokenMinutes <= 0 || refreshTokenDays <= 0) {
            throw new IllegalArgumentException("Token lifetimes must be positive.");
        }
    }

    /**
     * Issues an access token and persists only a digest of a random refresh token.
     *
     * @param user authenticated active account
     * @return access token and one-time refresh-token value for an HttpOnly cookie
     */
    @Transactional
    public TokenBundle issue(User user) {
        String refreshTokenValue = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(refreshTokenValue));
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenLifetime));
        refreshTokenRepository.save(refreshToken);
        return new TokenBundle(createAccessToken(user), refreshTokenValue, refreshToken.getExpiresAt(), user.getId());
    }

    /**
     * Atomically consumes a refresh token and replaces it with a new token pair.
     * A previously rotated token revokes all active refresh tokens for the account.
     *
     * @param rawToken cookie value supplied by the client
     * @return rotation result, including a reuse signal when the credential was replayed
     */
    @Transactional
    public RefreshResult rotate(String rawToken) {
        String hash = hashToken(rawToken);
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid."));
        Instant now = Instant.now();
        if (current.getRevokedAt() != null) {
            if (current.getReplacedBy() != null) {
                refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(current.getUser().getId())
                        .forEach(token -> token.setRevokedAt(now));
                return new RefreshResult(null, RefreshFailure.REUSE_DETECTED);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid.");
        }
        if (!current.getExpiresAt().isAfter(now)) {
            current.setRevokedAt(now);
            return new RefreshResult(null, RefreshFailure.EXPIRED);
        }
        current.setLastUsedAt(now);
        TokenBundle replacement = issue(current.getUser());
        current.setRevokedAt(now);
        current.setReplacedBy(refreshTokenRepository.findByTokenHashForUpdate(hashToken(replacement.refreshToken()))
                .orElseThrow(() -> new IllegalStateException("Replacement refresh token was not persisted.")));
        return new RefreshResult(replacement, RefreshFailure.NONE);
    }

    /**
     * Revokes a refresh token when present; invalid or absent cookies are harmless on logout.
     *
     * @param rawToken cookie value, if supplied
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private String createAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenLifetime))
                .id(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String generateOpaqueToken() {
        byte[] random = new byte[48];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token.", exception);
        }
    }

    /**
     * Issued credentials; only the access value is returned in a JSON response.
     */
    public record TokenBundle(String accessToken, String refreshToken, Instant refreshExpiresAt, UUID userId) {
    }

    /**
     * Rotation result that lets the transaction commit reuse-triggered revocations.
     */
    public record RefreshResult(TokenBundle bundle, RefreshFailure failure) {
    }

    /**
     * Persisted refresh rejection outcomes evaluated after the rotation transaction commits.
     */
    public enum RefreshFailure {
        NONE,
        EXPIRED,
        REUSE_DETECTED
    }
}
