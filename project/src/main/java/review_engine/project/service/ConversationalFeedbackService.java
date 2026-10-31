package review_engine.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import review_engine.project.ai.AIProviderException;
import review_engine.project.ai.AIService;
import review_engine.project.ai.PromptTemplateService;
import review_engine.project.ai.dto.FollowUpEvaluation;
import review_engine.project.config.AIProperties;
import review_engine.project.dto.response.FeedbackConversationResponse;

import java.util.Map;

/**
 * Adds optional AI follow-ups after the Phase 4 message transaction has completed.
 */
@Service
public class ConversationalFeedbackService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationalFeedbackService.class);
    private final FeedbackConversationService conversations;
    private final AIService ai;
    private final PromptTemplateService prompts;
    private final AIProperties properties;

    public ConversationalFeedbackService(FeedbackConversationService conversations, AIService ai,
                                         PromptTemplateService prompts, AIProperties properties) {
        this.conversations = conversations;
        this.ai = ai;
        this.prompts = prompts;
        this.properties = properties;
    }

    public FeedbackConversationResponse addMessage(String token, String content, Integer rating) {
        FeedbackConversationService.CustomerMessageResult saved =
                conversations.addCustomerMessageOnce(token, content, rating);
        if (!saved.newlyAccepted() || !ai.isEnabled() || properties.getMaxFollowUpQuestions() == 0) {
            return saved.response();
        }
        FeedbackConversationService.ConversationContext context = conversations.context(token);
        if (context.followUpCount() >= properties.getMaxFollowUpQuestions()) {
            return saved.response();
        }
        try {
            FollowUpEvaluation result = ai.generate(prompts.conversationEvaluation(),
                    ai.contextJson(Map.of("businessName", context.businessName(),
                            "conversation", context.messages(), "feedbackQuestions", context.questions(),
                            "followUpCount", context.followUpCount())),
                    FollowUpEvaluation.class);
            if (!Boolean.TRUE.equals(result.needsFollowUp())
                    || context.followUpCount() >= properties.getMaxFollowUpQuestions()) {
                return saved.response();
            }
            return conversations.addAiFollowUp(token, result.followUpQuestion());
        } catch (AIProviderException exception) {
            LOGGER.warn("AI follow-up generation failed; customer may continue with structured feedback.");
            return saved.response();
        }
    }
}
