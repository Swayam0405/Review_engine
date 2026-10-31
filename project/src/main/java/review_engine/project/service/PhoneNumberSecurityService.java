package review_engine.project.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import review_engine.project.exception.ApiException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Normalizes phone numbers and protects them with AES-GCM and a separate keyed lookup hash.
 */
@Service
public class PhoneNumberSecurityService {
    private static final String ENCRYPTION_VERSION = "v1";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    private final byte[] encryptionKey;
    private final byte[] hmacKey;
    private final String defaultRegion;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Loads independent cryptographic keys from secret configuration and rejects weak material.
     */
    public PhoneNumberSecurityService(
            @Value("${app.customer.phone.encryption-key}") String encodedEncryptionKey,
            @Value("${app.customer.phone.hmac-key}") String encodedHmacKey,
            @Value("${app.customer.phone.default-region:ZZ}") String defaultRegion) {
        this.encryptionKey = decodeKey(encodedEncryptionKey, 32, "Phone encryption key must be a Base64-encoded 256-bit key.");
        this.hmacKey = decodeKey(encodedHmacKey, 32, "Phone lookup HMAC key must contain at least 256 bits.");
        if (encryptionKey.length != 32 || hmacKey.length < 32) {
            throw new IllegalArgumentException("Phone security keys have invalid lengths.");
        }
        if (MessageDigest.isEqual(encryptionKey, hmacKey)) {
            throw new IllegalArgumentException("Phone encryption and lookup keys must be different.");
        }
        this.defaultRegion = defaultRegion.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Converts a valid number to canonical E.164 form using only international input or the configured region.
     */
    public String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid phone number is required.");
        }
        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(phone.strip(), defaultRegion);
            if (!phoneNumberUtil.isValidNumber(parsed) || parsed.hasExtension()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A valid phone number is required.");
            }
            return phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid phone number is required.");
        }
    }

    /**
     * Produces a deterministic business-scoped-search input without revealing the normalized phone.
     */
    public String lookupHash(String normalizedPhone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Phone lookup hashing is unavailable.", exception);
        }
    }

    /**
     * Encrypts E.164 phone data with a fresh 96-bit nonce for every invocation.
     *
     * @return versioned Base64 representation: version, nonce, and authenticated ciphertext
     */
    public String encrypt(String normalizedPhone) {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(ENCRYPTION_VERSION.getBytes(StandardCharsets.US_ASCII));
            byte[] ciphertext = cipher.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return ENCRYPTION_VERSION + ":" + encoder.encodeToString(nonce) + ":" + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Phone encryption is unavailable.", exception);
        }
    }

    /**
     * Authenticates and decrypts a phone number for an authorized response.
     */
    public String decrypt(String encryptedPhone) {
        try {
            String[] parts = encryptedPhone.split(":", -1);
            if (parts.length != 3 || !ENCRYPTION_VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported phone encryption format.");
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] nonce = decoder.decode(parts[1]);
            byte[] ciphertext = decoder.decode(parts[2]);
            if (nonce.length != NONCE_LENGTH || ciphertext.length <= TAG_LENGTH_BITS / 8) {
                throw new IllegalArgumentException("Invalid phone encryption format.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(ENCRYPTION_VERSION.getBytes(StandardCharsets.US_ASCII));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored phone data could not be authenticated.", exception);
        }
    }

    private byte[] decodeKey(String encodedKey, int minimumBytes, String errorMessage) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length < minimumBytes) {
                throw new IllegalArgumentException(errorMessage);
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
