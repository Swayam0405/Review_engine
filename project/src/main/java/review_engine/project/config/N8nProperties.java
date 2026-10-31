package review_engine.project.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * n8n webhooks and shared secret configuration.
 */
@ConfigurationProperties(prefix = "review-engine.n8n")
public class N8nProperties {
    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String internalApiKey;
    private String feedbackScheduleWebhook;
    private String callbackSecret;
    private long pollIntervalMs = 5000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getInternalApiKey() { return internalApiKey; }
    public void setInternalApiKey(String internalApiKey) { this.internalApiKey = internalApiKey; }
    public String getFeedbackScheduleWebhook() { return feedbackScheduleWebhook; }
    public void setFeedbackScheduleWebhook(String feedbackScheduleWebhook) {
        this.feedbackScheduleWebhook = feedbackScheduleWebhook;
    }
    public String getCallbackSecret() { return callbackSecret; }
    public void setCallbackSecret(String callbackSecret) { this.callbackSecret = callbackSecret; }
    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

    @PostConstruct
    void validate() {
        if (pollIntervalMs < 1000) {
            throw new IllegalStateException("n8n polling interval must be at least 1000 milliseconds.");
        }
        if (!enabled) {
            return;
        }
        if (blank(baseUrl) || blank(apiKey) || blank(internalApiKey)
                || blank(feedbackScheduleWebhook) || blank(callbackSecret)) {
            throw new IllegalStateException("Enabled n8n integration requires all URL and secret settings.");
        }
        requireSecureHttpUrl(baseUrl, "n8n base URL");
        if (URI.create(feedbackScheduleWebhook).isAbsolute()) {
            requireSecureHttpUrl(feedbackScheduleWebhook, "n8n schedule webhook URL");
        }
        if (internalApiKey.getBytes(StandardCharsets.UTF_8).length < 32
                || callbackSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("n8n internal and callback secrets must contain at least 32 bytes.");
        }
    }

    private void requireSecureHttpUrl(String value, String setting) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(setting + " must be a valid absolute URL.", exception);
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                || "::1".equals(uri.getHost()));
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                || (!https && !localHttp)) {
            throw new IllegalStateException(setting + " must use HTTPS (HTTP is allowed only for localhost).");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
