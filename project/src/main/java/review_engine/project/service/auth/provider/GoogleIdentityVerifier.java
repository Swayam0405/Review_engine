package review_engine.project.service.auth.provider;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import review_engine.project.exception.ApiException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Verifies Google ID-token signature, issuer, audience and expiration using Google's library.
 */
@Component
public class GoogleIdentityVerifier {
    private final String clientId;

    public GoogleIdentityVerifier(@Value("${app.security.google.client-id}") String clientId) {
        this.clientId = clientId;
    }

    /**
     * Validates a client ID token and extracts its stable subject and verified email.
     *
     * @param idTokenValue token produced by Google's sign-in flow
     * @return validated Google identity claims
     * @throws ApiException if configuration is missing or the token is invalid/unverified
     */
    public VerifiedGoogleIdentity verify(String idTokenValue) {
        if (clientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google Sign-In is not configured.");
        }
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(List.of(clientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(idTokenValue);
            if (idToken == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Google identity token is invalid.");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())
                    || payload.getSubject() == null
                    || payload.getEmail() == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Google identity token is invalid.");
            }
            Object nameClaim = payload.get("name");
            return new VerifiedGoogleIdentity(payload.getSubject(), payload.getEmail(),
                    nameClaim instanceof String name ? name : null);
        } catch (IOException | GeneralSecurityException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google identity token is invalid.");
        }
    }
}
