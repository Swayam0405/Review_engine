package review_engine.project.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Feedback token, public-link, and expiration settings.
 */
@ConfigurationProperties(prefix = "review-engine.feedback")
public class FeedbackProperties {
    private Duration tokenExpiry = Duration.ofDays(14);
    private String publicBaseUrl = "http://localhost:3000/feedback";
    private int rateLimitPerMinute = 30;
    private String tokenSecret;

    public Duration getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(Duration tokenExpiry) { this.tokenExpiry = tokenExpiry; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
    public String getTokenSecret() { return tokenSecret; }
    public void setTokenSecret(String tokenSecret) { this.tokenSecret = tokenSecret; }

    @PostConstruct
    void validate() {
        if (tokenExpiry == null || tokenExpiry.isZero() || tokenExpiry.isNegative()) {
            throw new IllegalStateException("Feedback token expiry must be positive.");
        }
        if (rateLimitPerMinute < 1) {
            throw new IllegalStateException("Feedback rate limit must be positive.");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("Feedback public base URL must be an absolute HTTP(S) URL.");
        }
        try {
            URI uri = URI.create(publicBaseUrl);
            if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException("Feedback public base URL must be an absolute HTTP(S) URL.");
            }
            if ("http".equalsIgnoreCase(uri.getScheme())
                    && !"localhost".equalsIgnoreCase(uri.getHost())
                    && !"127.0.0.1".equals(uri.getHost()) && !"::1".equals(uri.getHost())) {
                throw new IllegalStateException("Feedback public base URL must use HTTPS outside localhost.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Feedback public base URL must be an absolute HTTP(S) URL.", exception);
        }
        if (tokenSecret == null || tokenSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("FEEDBACK_TOKEN_SECRET must contain at least 32 bytes.");
        }
    }
}
