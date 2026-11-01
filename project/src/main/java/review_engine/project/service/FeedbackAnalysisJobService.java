package review_engine.project.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.ai.dto.FeedbackAnalysisResult;
import review_engine.project.entity.FeedbackAnalysis;
import review_engine.project.entity.FeedbackOutboxEvent;
import review_engine.project.enums.FeedbackOutboxStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.FeedbackAnalysisRepository;
import review_engine.project.repository.FeedbackAnswerRepository;
import review_engine.project.repository.FeedbackOutboxRepository;
import review_engine.project.repository.FeedbackRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Claims and records analysis jobs in short database transactions; provider calls happen separately.
 */
@Service
public class FeedbackAnalysisJobService {
    public static final String EVENT_TYPE = "FEEDBACK_ANALYSIS";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE = Duration.ofMinutes(5);
    private final FeedbackOutboxRepository outboxRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackAnalysisRepository analysisRepository;
    private final FeedbackAnswerRepository answerRepository;
    private final BusinessScopeService businessScope;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FeedbackAnalysisJobService(FeedbackOutboxRepository outboxRepository, FeedbackRepository feedbackRepository,
                                      FeedbackAnalysisRepository analysisRepository,
                                      FeedbackAnswerRepository answerRepository, BusinessScopeService businessScope,
                                      ObjectMapper objectMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.feedbackRepository = feedbackRepository;
        this.analysisRepository = analysisRepository;
        this.answerRepository = answerRepository;
        this.businessScope = businessScope;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Optional<AnalysisJob> claimNext() {
        Instant now = clock.instant();
        for (FeedbackOutboxEvent event : outboxRepository.findReadyForUpdate(
                EVENT_TYPE, now, PageRequest.of(0, 20))) {
            if (event.getStatus() == FeedbackOutboxStatus.PROCESSING
                    && event.getAttemptCount() >= MAX_ATTEMPTS) {
                event.setStatus(FeedbackOutboxStatus.FAILED);
                event.setLastError("Analysis retry limit reached.");
                continue;
            }
            event.setStatus(FeedbackOutboxStatus.PROCESSING);
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setNextAttemptAt(now.plus(LEASE));
            return Optional.of(new AnalysisJob(event.getId(), event.getAggregateId(), event.getAttemptCount()));
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadFeedbackContext(UUID feedbackId) {
        var feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalStateException("Analysis feedback record is missing."));
        var answers = answerRepository.findAllByFeedbackId(feedbackId).stream()
                .map(answer -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("question", answer.getQuestion().getQuestionText());
                    item.put("answer", answer.getAnswerText() != null ? answer.getAnswerText()
                            : answer.getAnswerRating() != null ? answer.getAnswerRating()
                            : answer.getAnswerBoolean());
                    return item;
                }).toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("rating", feedback.getRating());
        context.put("comment", feedback.getComment());
        context.put("answers", answers);
        context.put("businessCategory", feedback.getBusiness().getCategory());
        return context;
    }

    @Transactional
    public void complete(AnalysisJob job, FeedbackAnalysisResult result, String provider, String model) {
        FeedbackOutboxEvent event = requireProcessing(job);
        if (analysisRepository.existsByFeedbackId(job.feedbackId())) {
            event.setStatus(FeedbackOutboxStatus.PROCESSED);
            event.setProcessedAt(clock.instant());
            event.setLastError(null);
            return;
        }
        FeedbackAnalysis analysis = new FeedbackAnalysis();
        var feedback = feedbackRepository.findById(job.feedbackId())
                .orElseThrow(() -> new IllegalStateException("Analysis feedback record is missing."));
        Instant now = clock.instant();
        analysis.setBusiness(feedback.getBusiness());
        analysis.setFeedback(feedback);
        analysis.setSentiment(result.sentiment());
        analysis.setSentimentConfidence(result.sentimentConfidence());
        analysis.setSentimentSummary(result.sentimentSummary());
        analysis.setTopics(json(result.topics()));
        analysis.setAspects(json(result.aspects()));
        analysis.setIssues(json(result.issues()));
        analysis.setPositiveHighlights(json(result.positiveHighlights()));
        analysis.setNegativeHighlights(json(result.negativeHighlights()));
        analysis.setImprovementOpportunities(json(result.improvementOpportunities()));
        analysis.setAiProvider(provider);
        analysis.setAiModel(model);
        analysis.setAnalyzedAt(now);
        analysisRepository.save(analysis);
        event.setStatus(FeedbackOutboxStatus.PROCESSED);
        event.setProcessedAt(now);
        event.setLastError(null);
    }

    @Transactional
    public void fail(AnalysisJob job, String safeMessage) {
        FeedbackOutboxEvent event = outboxRepository.findById(job.eventId())
                .orElseThrow(() -> new IllegalStateException("Analysis job disappeared."));
        if (event.getStatus() != FeedbackOutboxStatus.PROCESSING || event.getAttemptCount() != job.attempt()) {
            return;
        }
        event.setLastError(safeMessage);
        if (event.getAttemptCount() >= MAX_ATTEMPTS) {
            event.setStatus(FeedbackOutboxStatus.FAILED);
        } else {
            event.setStatus(FeedbackOutboxStatus.PENDING);
            event.setNextAttemptAt(clock.instant().plusSeconds(
                    Math.min(300, 1L << Math.min(event.getAttemptCount(), 8))));
        }
    }

    @Transactional
    public void retry(UUID ownerId, UUID feedbackId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        if (!feedbackRepository.existsByIdAndBusinessId(feedbackId, businessId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Feedback was not found.");
        }
        FeedbackOutboxEvent event = outboxRepository.lockByEventTypeAndAggregateId(EVENT_TYPE, feedbackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis job was not found."));
        if (event.getStatus() != FeedbackOutboxStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only failed analysis jobs can be retried.");
        }
        event.setAttemptCount(0);
        event.setStatus(FeedbackOutboxStatus.PENDING);
        event.setNextAttemptAt(clock.instant());
        event.setLastError(null);
    }

    private FeedbackOutboxEvent requireProcessing(AnalysisJob job) {
        FeedbackOutboxEvent event = outboxRepository.findById(job.eventId())
                .orElseThrow(() -> new IllegalStateException("Analysis job disappeared."));
        if (event.getStatus() != FeedbackOutboxStatus.PROCESSING
                || event.getAttemptCount() != job.attempt()) {
            throw new IllegalStateException("Analysis job is no longer claimed.");
        }
        return event;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Validated analysis could not be serialized.", exception);
        }
    }

    public record AnalysisJob(UUID eventId, UUID feedbackId, int attempt) {
    }
}
