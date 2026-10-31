package review_engine.project.service;

import org.springframework.stereotype.Service;
import review_engine.project.config.FeedbackProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Produces opaque high-entropy links and persists only a one-way SHA-256 token digest.
 *
 * <p>The feedback request UUID is generated with Java's cryptographically secure UUID
 * generator. A separate HMAC secret derives a stable 256-bit opaque token, allowing a
 * delivery worker to reconstruct the link without storing the raw token.</p>
 */
@Service
public class FeedbackTokenService {
    private final byte[] key;

    public FeedbackTokenService(FeedbackProperties properties) {
        String secret = properties.getTokenSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("FEEDBACK_TOKEN_SECRET must contain at least 32 bytes.");
        }
        key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String tokenFor(UUID requestId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(("feedback:v1:" + requestId)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Feedback token generation is unavailable.", exception);
        }
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Feedback token validation is unavailable.", exception);
        }
    }

    public boolean matches(String token, String expectedHash) {
        return MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
