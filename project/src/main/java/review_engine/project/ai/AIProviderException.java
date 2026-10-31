package review_engine.project.ai;

/**
 * Sanitized AI integration failure; the cause is retained for internal diagnostics only.
 */
public class AIProviderException extends RuntimeException {
    public AIProviderException(String message) {
        super(message);
    }

    public AIProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
