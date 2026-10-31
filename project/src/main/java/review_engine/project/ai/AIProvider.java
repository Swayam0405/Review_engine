package review_engine.project.ai;

/**
 * Provider-independent interface for requesting one structured AI response.
 */
public interface AIProvider {
    /**
     * Requests JSON using separate system rules and untrusted user-context payloads.
     */
    String generateJson(String systemPrompt, String userContext);

    String providerName();

    String modelName();
}
