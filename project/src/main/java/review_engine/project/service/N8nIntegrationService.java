package review_engine.project.service;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import review_engine.project.config.N8nProperties;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * Sends idempotent minimal scheduling events to n8n without moving business rules out of Spring.
 */
@Service
public class N8nIntegrationService {
    private final N8nProperties properties;
    private final RestClient restClient;

    public N8nIntegrationService(N8nProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void schedule(UUID eventId, String payload) {
        String url = properties.getFeedbackScheduleWebhook();
        if (!java.net.URI.create(url).isAbsolute()) {
            url = properties.getBaseUrl().replaceAll("/+$", "") + "/" + url.replaceAll("^/+", "");
        }
        try {
            restClient.post().uri(url)
                    .header("X-N8N-API-KEY", properties.getApiKey())
                    .header("Idempotency-Key", eventId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new N8nDeliveryException("n8n scheduling request failed.", exception);
        }
    }

    public boolean matchesInternalSecret(String supplied) {
        return constantTimeEquals(properties.getInternalApiKey(), supplied);
    }

    public boolean matchesCallbackSecret(String supplied) {
        return constantTimeEquals(properties.getCallbackSecret(), supplied);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Signals a retryable integration failure without including credentials or payload content.
     */
    public static class N8nDeliveryException extends RuntimeException {
        public N8nDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
