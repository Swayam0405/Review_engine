package review_engine.project;

import org.junit.jupiter.api.Test;
import review_engine.project.service.PhoneNumberSecurityService;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies phone normalization, independent lookup hashing, and authenticated encryption behavior.
 */
class PhoneNumberSecurityServiceTests {
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String HMAC_KEY = createHmacKey();

    private static String createHmacKey() {
        byte[] hmacBytes = new byte[32];
        java.util.Arrays.fill(hmacBytes, (byte) 1);
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    @Test
    void normalizesValidNumbersToE164AndRejectsImpossibleNumbers() {
        PhoneNumberSecurityService security = newService("ZZ");

        assertEquals("+14155552671", security.normalize("+1 (415) 555-2671"));
        assertEquals("+919876543210", security.normalize("+919876543210"));
        assertThrows(RuntimeException.class, () -> security.normalize("+10000000000"));
    }

    @Test
    void encryptsAndDecryptsWithFreshNoncesAndAuthenticatesCiphertext() {
        PhoneNumberSecurityService security = newService("ZZ");
        String normalized = "+14155552671";
        String first = security.encrypt(normalized);
        String second = security.encrypt(normalized);

        assertEquals(normalized, security.decrypt(first));
        assertNotEquals(first, second);
        assertEquals(3, first.split(":").length);

        String[] parts = first.split(":");
        byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
        ciphertext[0] ^= 1;
        String tampered = parts[0] + ":" + parts[1] + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        assertThrows(IllegalStateException.class, () -> security.decrypt(tampered));
    }

    @Test
    void lookupHashIsDeterministicAndSeparatesNumbers() {
        PhoneNumberSecurityService security = newService("ZZ");

        assertEquals(security.lookupHash("+14155552671"), security.lookupHash("+14155552671"));
        assertNotEquals(security.lookupHash("+14155552671"), security.lookupHash("+14155552672"));
    }

    @Test
    void rejectsInvalidCryptographicKeyConfiguration() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalArgumentException.class,
                () -> new PhoneNumberSecurityService(shortKey, HMAC_KEY, "ZZ"));
        assertThrows(IllegalArgumentException.class,
                () -> new PhoneNumberSecurityService(ENCRYPTION_KEY, "not-base64", "ZZ"));
        assertThrows(IllegalArgumentException.class,
                () -> new PhoneNumberSecurityService(ENCRYPTION_KEY, ENCRYPTION_KEY, "ZZ"));
    }

    private PhoneNumberSecurityService newService(String region) {
        return new PhoneNumberSecurityService(ENCRYPTION_KEY, HMAC_KEY, region);
    }
}
