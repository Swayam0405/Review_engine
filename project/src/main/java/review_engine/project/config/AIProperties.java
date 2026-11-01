package review_engine.project.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Validated AI provider, prompt-size, retry, and conversational limit settings.
 */
@ConfigurationProperties(prefix = "review-engine.ai")
public class AIProperties {
    private boolean enabled;
    private String provider = "openai-compatible";
    private String apiKey;
    private String model = "gpt-4o-mini";
    private String baseUrl = "https://api.openai.com/v1";
    private Duration timeout = Duration.ofSeconds(20);
    private int maxRetries = 2;
    private int maxPromptCharacters = 20_000;
    private int maxFollowUpQuestions = 3;
    private int maxAnalysisBatch = 20;
    private int rateLimitPerMinute = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int getMaxPromptCharacters() { return maxPromptCharacters; }
    public void setMaxPromptCharacters(int maxPromptCharacters) { this.maxPromptCharacters = maxPromptCharacters; }
    public int getMaxFollowUpQuestions() { return maxFollowUpQuestions; }
    public void setMaxFollowUpQuestions(int maxFollowUpQuestions) { this.maxFollowUpQuestions = maxFollowUpQuestions; }
    public int getMaxAnalysisBatch() { return maxAnalysisBatch; }
    public void setMaxAnalysisBatch(int maxAnalysisBatch) { this.maxAnalysisBatch = maxAnalysisBatch; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    @PostConstruct
    void validate() {
        if (!"openai-compatible".equals(provider)) {
            throw new IllegalStateException("Unsupported AI provider configured.");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(2)) > 0
                || maxRetries < 0 || maxRetries > 5
                || maxPromptCharacters < 1000 || maxPromptCharacters > 100_000
                || maxFollowUpQuestions < 0 || maxFollowUpQuestions > 10
                || maxAnalysisBatch < 1 || maxAnalysisBatch > 100
                || rateLimitPerMinute < 1 || rateLimitPerMinute > 100) {
            throw new IllegalStateException("AI timeout, retry, prompt, or batch settings are outside allowed bounds.");
        }
        if (enabled && (apiKey == null || apiKey.isBlank() || model == null || model.isBlank())) {
            throw new IllegalStateException("Enabled AI requires an API key and model.");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AI base URL must be a valid absolute URL.", exception);
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                || (!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp)) {
            throw new IllegalStateException("AI base URL must use HTTPS except for localhost testing.");
        }
    }
}
