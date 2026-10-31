package review_engine.project.ai;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import review_engine.project.config.AIProperties;
import review_engine.project.exception.ApiException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Central structured-output boundary; malformed provider content never reaches persistence.
 */
@Service
public class AIService {
    private final AIProperties properties;
    private final AIProvider provider;
    private final ObjectMapper objectMapper;
    private final AIResponseParser parser;

    public AIService(AIProperties properties, AIProvider provider, ObjectMapper objectMapper) {
        this.properties = properties;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.parser = new AIResponseParser(objectMapper);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public <T> T generate(String systemPrompt, String userContext, Class<T> responseType) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI features are currently disabled.");
        }
        if (systemPrompt.length() + userContext.length() > properties.getMaxPromptCharacters()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AI context exceeds the configured size limit.");
        }
        T response = parser.parse(provider.generateJson(systemPrompt, userContext), responseType);
        AIResponseValidator.validate(response);
        return response;
    }

    public String contextJson(Map<String, ?> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (RuntimeException exception) {
            throw new AIProviderException("AI context could not be prepared.", exception);
        }
    }

    public String providerName() {
        return provider.providerName();
    }

    public String modelName() {
        return provider.modelName();
    }
}
