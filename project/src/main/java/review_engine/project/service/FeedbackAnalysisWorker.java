package review_engine.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import review_engine.project.ai.AIProviderException;
import review_engine.project.ai.AIService;
import review_engine.project.ai.PromptTemplateService;
import review_engine.project.ai.dto.FeedbackAnalysisResult;

/**
 * Processes durable feedback-analysis outbox events without holding a transaction during AI calls.
 */
@Component
public class FeedbackAnalysisWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackAnalysisWorker.class);
    private final FeedbackAnalysisJobService jobs;
    private final AIService ai;
    private final PromptTemplateService prompts;

    public FeedbackAnalysisWorker(FeedbackAnalysisJobService jobs, AIService ai, PromptTemplateService prompts) {
        this.jobs = jobs;
        this.ai = ai;
        this.prompts = prompts;
    }

    @Scheduled(fixedDelayString = "${review-engine.ai.worker-interval:5s}")
    public void processBatch() {
        if (!ai.isEnabled()) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            var claimed = jobs.claimNext();
            if (claimed.isEmpty()) {
                return;
            }
            var job = claimed.get();
            try {
                String context = ai.contextJson(jobs.loadFeedbackContext(job.feedbackId()));
                FeedbackAnalysisResult result = ai.generate(prompts.feedbackAnalysis(), context,
                        FeedbackAnalysisResult.class);
                jobs.complete(job, result, ai.providerName(), ai.modelName());
            } catch (AIProviderException exception) {
                LOGGER.warn("Feedback analysis event {} failed on attempt {}.", job.eventId(), job.attempt());
                jobs.fail(job, exception.getMessage());
            }
        }
    }
}
