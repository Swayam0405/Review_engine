package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.request.FeedbackAnswerRequest;
import review_engine.project.dto.request.FeedbackSubmissionRequest;
import review_engine.project.dto.response.FeedbackConfirmationResponse;
import review_engine.project.entity.Feedback;
import review_engine.project.entity.FeedbackAnswer;
import review_engine.project.entity.FeedbackConversation;
import review_engine.project.entity.FeedbackQuestion;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.enums.ConversationMessageType;
import review_engine.project.enums.ConversationSenderType;
import review_engine.project.enums.FeedbackConversationStatus;
import review_engine.project.enums.FeedbackQuestionType;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.FeedbackAnswerRepository;
import review_engine.project.repository.FeedbackQuestionRepository;
import review_engine.project.repository.FeedbackRepository;
import review_engine.project.repository.FeedbackOutboxRepository;
import review_engine.project.entity.FeedbackOutboxEvent;
import review_engine.project.enums.FeedbackOutboxStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates and transactionally stores final feedback, typed answers, messages, and lifecycle changes.
 */
@Service
public class FeedbackSubmissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackSubmissionService.class);
    private final FeedbackRequestService requestService;
    private final FeedbackConversationService conversationService;
    private final FeedbackQuestionRepository questionRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackAnswerRepository answerRepository;
    private final FeedbackOutboxRepository outboxRepository;
    private final Clock clock;

    public FeedbackSubmissionService(FeedbackRequestService requestService,
                                     FeedbackConversationService conversationService,
                                     FeedbackQuestionRepository questionRepository,
                                     FeedbackRepository feedbackRepository,
                                     FeedbackAnswerRepository answerRepository,
                                     FeedbackOutboxRepository outboxRepository, Clock clock) {
        this.requestService = requestService;
        this.conversationService = conversationService;
        this.questionRepository = questionRepository;
        this.feedbackRepository = feedbackRepository;
        this.answerRepository = answerRepository;
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Transactional
    public FeedbackConfirmationResponse submit(String token, FeedbackSubmissionRequest submission) {
        FeedbackRequest request = requestService.requireValidToken(token, true);
        if (feedbackRepository.existsByFeedbackRequestId(request.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Feedback has already been submitted.");
        }
        FeedbackConversation conversation = conversationService.requireActiveConversation(request);
        if (submission.rating() == null || submission.rating() < 1 || submission.rating() > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Overall rating must be between 1 and 5.");
        }
        String comment = submission.comment() == null ? null : submission.comment().strip();
        if (comment != null && (comment.isEmpty() || comment.length() > 5000
                || comment.codePoints().anyMatch(Character::isISOControl))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Comment must contain 1 to 5000 valid characters.");
        }

        var activeQuestions = questionRepository.findAllByBusinessIdAndActiveTrueOrderByDisplayOrderAscIdAsc(
                request.getBusiness().getId());
        Map<UUID, FeedbackQuestion> questionsById = activeQuestions.stream()
                .collect(Collectors.toMap(FeedbackQuestion::getId, Function.identity()));
        Map<UUID, FeedbackAnswerRequest> answersByQuestion = new HashMap<>();
        if (submission.answers() != null) {
            for (FeedbackAnswerRequest answer : submission.answers()) {
                if (answersByQuestion.putIfAbsent(answer.questionId(), answer) != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "A question may be answered only once.");
                }
                FeedbackQuestion question = questionsById.get(answer.questionId());
                if (question == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "An answer references an unavailable question.");
                }
                validateAnswer(question, answer);
            }
        }
        for (FeedbackQuestion question : activeQuestions) {
            if (question.isRequired() && !answersByQuestion.containsKey(question.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "All required questions must be answered.");
            }
        }

        Instant now = clock.instant();
        Feedback feedback = new Feedback();
        feedback.setBusiness(request.getBusiness());
        feedback.setTransaction(request.getTransaction());
        feedback.setCustomer(request.getCustomer());
        feedback.setFeedbackRequest(request);
        feedback.setConversation(conversation);
        feedback.setRating(submission.rating());
        feedback.setComment(comment);
        feedback.setSubmittedAt(now);
        feedbackRepository.save(feedback);
        FeedbackOutboxEvent analysisJob = new FeedbackOutboxEvent();
        analysisJob.setId(UUID.randomUUID());
        analysisJob.setEventType("FEEDBACK_ANALYSIS");
        analysisJob.setAggregateId(feedback.getId());
        analysisJob.setPayload("{\"feedbackId\":\"" + feedback.getId() + "\"}");
        analysisJob.setStatus(FeedbackOutboxStatus.PENDING);
        analysisJob.setNextAttemptAt(now);
        outboxRepository.save(analysisJob);

        for (FeedbackAnswerRequest submittedAnswer : answersByQuestion.values()) {
            FeedbackAnswer answer = new FeedbackAnswer();
            answer.setBusiness(request.getBusiness());
            answer.setFeedback(feedback);
            answer.setQuestion(questionsById.get(submittedAnswer.questionId()));
            answer.setAnswerText(submittedAnswer.answerText() == null ? null : submittedAnswer.answerText().strip());
            answer.setAnswerRating(submittedAnswer.answerRating());
            answer.setAnswerBoolean(submittedAnswer.answerBoolean());
            answerRepository.save(answer);
            String display = submittedAnswer.answerText() != null ? submittedAnswer.answerText().strip()
                    : submittedAnswer.answerRating() != null ? submittedAnswer.answerRating().toString()
                    : submittedAnswer.answerBoolean().toString();
            conversationService.saveMessage(conversation, ConversationSenderType.CUSTOMER,
                    answerType(questionsById.get(submittedAnswer.questionId()).getQuestionType()), display);
        }
        conversationService.saveMessage(conversation, ConversationSenderType.CUSTOMER,
                ConversationMessageType.RATING, "Rating: " + submission.rating());
        if (comment != null) {
            conversationService.saveMessage(conversation, ConversationSenderType.CUSTOMER,
                    ConversationMessageType.TEXT, comment);
        }
        conversation.setStatus(FeedbackConversationStatus.COMPLETED);
        conversation.setCompletedAt(now);
        request.setStatus(FeedbackRequestStatus.COMPLETED);
        request.setCompletedAt(now);
        String configuredReviewUrl = request.getBusiness().getGoogleReviewUrl();
        boolean reviewAvailable = GoogleReviewUrlValidator.isValid(configuredReviewUrl);
        if (configuredReviewUrl != null && !configuredReviewUrl.isBlank() && !reviewAvailable) {
            LOGGER.warn("Business {} has an invalid configured Google review URL; feedback completed without a link.",
                    request.getBusiness().getId());
        }
        String completionMessage = "Thank you. Your feedback has been received.";
        String invitation = reviewAvailable
                ? "If you would like to share your experience publicly, you can leave an optional Google review."
                : null;
        return new FeedbackConfirmationResponse(true, completionMessage, true, true, completionMessage,
                reviewAvailable, reviewAvailable ? GoogleReviewUrlValidator.validate(configuredReviewUrl) : null,
                invitation);
    }

    private void validateAnswer(FeedbackQuestion question, FeedbackAnswerRequest answer) {
        int supplied = (answer.answerText() == null ? 0 : 1)
                + (answer.answerRating() == null ? 0 : 1)
                + (answer.answerBoolean() == null ? 0 : 1);
        boolean valid = switch (question.getQuestionType()) {
            case TEXT -> supplied == 1 && answer.answerText() != null
                    && !answer.answerText().isBlank() && answer.answerText().length() <= 5000
                    && answer.answerText().codePoints().noneMatch(Character::isISOControl);
            case RATING -> supplied == 1 && answer.answerRating() != null
                    && answer.answerRating() >= 1 && answer.answerRating() <= 5;
            case YES_NO -> supplied == 1 && answer.answerBoolean() != null;
        };
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Answer format does not match its feedback question.");
        }
    }

    private ConversationMessageType answerType(FeedbackQuestionType type) {
        return switch (type) {
            case TEXT, YES_NO -> ConversationMessageType.ANSWER;
            case RATING -> ConversationMessageType.RATING;
        };
    }
}
