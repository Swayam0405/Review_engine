package review_engine.project.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Generates and compares keyed OTP digests without persisting the verification code.
 */
@Component
public class OtpHasher {
    private final byte[] secret;

    public OtpHasher(@Value("${app.security.otp.hmac-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("OTP_HMAC_SECRET must contain at least 32 bytes.");
        }
    }

    /**
     * Creates a keyed HMAC-SHA-256 digest scoped to the normalized email identity.
     *
     * @param email normalized identity subject
     * @param code six-digit one-time code
     * @return lowercase hexadecimal digest
     */
    public String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash email verification code.", exception);
        }
    }

    /**
     * Compares an OTP with its stored digest using a constant-time byte comparison.
     *
     * @param email normalized identity subject
     * @param code submitted six-digit code
     * @param expectedHash stored HMAC digest
     * @return true when the code matches
     */
    public boolean matches(String email, String code, String expectedHash) {
        byte[] actual = hash(email, code).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }
}
