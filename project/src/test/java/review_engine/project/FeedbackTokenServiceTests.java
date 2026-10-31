package review_engine.project;

import org.junit.jupiter.api.Test;
import review_engine.project.config.FeedbackProperties;
import review_engine.project.service.FeedbackTokenService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackTokenServiceTests {
    @Test
    void derivesStableOpaqueTokensAndStoresOnlyTheirDigest() {
        FeedbackProperties properties = new FeedbackProperties();
        properties.setTokenSecret("test-feedback-token-secret-at-least-thirty-two-bytes");
        FeedbackTokenService service = new FeedbackTokenService(properties);
        UUID requestId = UUID.randomUUID();

        String token = service.tokenFor(requestId);

        assertEquals(token, service.tokenFor(requestId));
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(service.matches(token, service.hash(token)));
        assertFalse(service.matches(token + "x", service.hash(token)));
        assertNotEquals(token, service.hash(token));
    }

    @Test
    void rejectsMissingOrShortSigningSecret() {
        FeedbackProperties properties = new FeedbackProperties();
        assertThrows(IllegalArgumentException.class, () -> new FeedbackTokenService(properties));

        properties.setTokenSecret("too-short");
        assertThrows(IllegalArgumentException.class, () -> new FeedbackTokenService(properties));
    }
}
