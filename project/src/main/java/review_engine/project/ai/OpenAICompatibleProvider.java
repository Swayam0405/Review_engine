package review_engine.project.ai;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import review_engine.project.config.AIProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat-completions adapter with bounded retry and sanitized provider errors.
 */
@Component
public class OpenAICompatibleProvider implements AIProvider {
    private final AIProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAICompatibleProvider(AIProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String generateJson(String systemPrompt, String userContext) {
        if (!properties.isEnabled()) {
            throw new AIProviderException("AI features are disabled.");
        }
        Map<String, Object> request = Map.of(
                "model", properties.getModel(),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContext)));
        int attempt = 0;
        while (true) {
            try {
                String response = restClient.post()
                        .uri(properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions")
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .header("Content-Type", "application/json")
                        .body(request)
                        .retrieve()
                        .body(String.class);
                return extractContent(response);
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                boolean transientFailure = status == 429 || exception.getStatusCode().is5xxServerError();
                if (!transientFailure || attempt >= properties.getMaxRetries()) {
                    throw new AIProviderException(status == 429
                            ? "AI provider is rate limiting requests."
                            : "AI provider rejected or could not complete the request.");
                }
            } catch (ResourceAccessException exception) {
                if (attempt >= properties.getMaxRetries()) {
                    throw new AIProviderException("AI provider timed out or is unavailable.", exception);
                }
            } catch (RestClientException exception) {
                throw new AIProviderException("AI provider request could not be completed.", exception);
            }
            sleepBeforeRetry(attempt++);
        }
    }

    private String extractContent(String response) {
        if (response == null || response.length() > properties.getMaxPromptCharacters() * 2L) {
            throw new AIProviderException("AI provider returned an empty or oversized response.");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asString().isBlank()) {
                throw new AIProviderException("AI provider returned no structured response.");
            }
            return content.asString();
        } catch (AIProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AIProviderException("AI provider returned an invalid response envelope.", exception);
        }
    }

    private void sleepBeforeRetry(int attempt) {
        long backoffMs = Math.min(4000, 250L << Math.min(attempt, 4));
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AIProviderException("AI provider retry was interrupted.", exception);
        }
    }

    @Override
    public String providerName() {
        return properties.getProvider();
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }
}
