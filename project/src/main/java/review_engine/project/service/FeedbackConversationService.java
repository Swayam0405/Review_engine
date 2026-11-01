package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.response.ConversationMessageResponse;
import review_engine.project.dto.response.FeedbackConversationResponse;
import review_engine.project.dto.response.FeedbackQuestionResponse;
import review_engine.project.dto.response.PublicFeedbackPageResponse;
import review_engine.project.entity.ConversationMessage;
import review_engine.project.entity.FeedbackConversation;
import review_engine.project.entity.FeedbackQuestion;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.enums.ConversationMessageType;
import review_engine.project.enums.ConversationSenderType;
import review_engine.project.enums.FeedbackConversationStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.ConversationMessageRepository;
import review_engine.project.repository.FeedbackConversationRepository;
import review_engine.project.repository.FeedbackQuestionRepository;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Runs the deterministic customer conversation flow; AI decision-making is intentionally deferred.
 */
@Service
public class FeedbackConversationService {
    private final FeedbackRequestService requestService;
    private final FeedbackConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final FeedbackQuestionRepository questionRepository;
    private final Clock clock;

    public FeedbackConversationService(FeedbackRequestService requestService,
                                       FeedbackConversationRepository conversationRepository,
                                       ConversationMessageRepository messageRepository,
                                       FeedbackQuestionRepository questionRepository, Clock clock) {
        this.requestService = requestService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicFeedbackPageResponse validateLink(String token) {
        FeedbackRequest request = requestService.requireValidToken(token, false);
        return new PublicFeedbackPageResponse(request.getBusiness().getName(), request.getExpiresAt(),
                activeQuestions(request));
    }

    @Transactional
    public FeedbackConversationResponse startOrResume(String token) {
        FeedbackRequest request = requestService.requireValidToken(token, true);
        FeedbackConversation conversation = conversationRepository.findByFeedbackRequestIdForUpdate(request.getId())
                .orElseGet(() -> createConversation(request));
        if (conversation.getStatus() != FeedbackConversationStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "This feedback conversation is no longer active.");
        }
        return response(conversation, request);
    }

    @Transactional(readOnly = true)
    public FeedbackConversationResponse get(String token) {
        FeedbackRequest request = requestService.requireValidToken(token, false);
        FeedbackConversation conversation = conversationRepository.findByFeedbackRequestId(request.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feedback conversation has not started."));
        return response(conversation, request);
    }

    @Transactional
    public FeedbackConversationResponse addCustomerMessage(String token, String content) {
        return addCustomerMessageOnce(token, content).response();
    }

    @Transactional
    public CustomerMessageResult addCustomerMessageOnce(String token, String content) {
        return addCustomerMessageOnce(token, content, null);
    }

    @Transactional
    public CustomerMessageResult addCustomerMessageOnce(String token, String content, Integer rating) {
        FeedbackRequest request = requestService.requireValidToken(token, true);
        FeedbackConversation conversation = conversationRepository.findByFeedbackRequestIdForUpdate(request.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Start the feedback conversation first."));
        if (conversation.getStatus() != FeedbackConversationStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "This feedback conversation is no longer active.");
        }
        String normalized = content == null ? "" : content.strip();
        if (normalized.isBlank() || normalized.length() > 10000
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Message must contain 1 to 10000 valid characters.");
        }
        String idempotencyKey = hashMessage(normalized + "|" + rating);
        var existing = messageRepository.findByConversationIdAndIdempotencyKey(conversation.getId(), idempotencyKey);
        if (existing.isPresent()) {
            return new CustomerMessageResult(response(conversation, request), false);
        }
        if (rating != null && messageRepository.countByConversationIdAndSenderTypeAndMessageType(
                conversation.getId(), ConversationSenderType.CUSTOMER, ConversationMessageType.RATING) == 0) {
            saveMessage(conversation, ConversationSenderType.CUSTOMER, ConversationMessageType.RATING,
                    "Initial rating: " + rating, hashMessage("initial-rating|" + rating));
        }
        saveMessage(conversation, ConversationSenderType.CUSTOMER, ConversationMessageType.TEXT,
                normalized, idempotencyKey);
        return new CustomerMessageResult(response(conversation, request), true);
    }

    @Transactional
    public FeedbackConversationResponse addAiFollowUp(String token, String question) {
        FeedbackRequest request = requestService.requireValidToken(token, true);
        FeedbackConversation conversation = conversationRepository.findByFeedbackRequestIdForUpdate(request.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Start the feedback conversation first."));
        if (conversation.getStatus() != FeedbackConversationStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "This feedback conversation is no longer active.");
        }
        saveMessage(conversation, ConversationSenderType.AI, ConversationMessageType.QUESTION, question);
        return response(conversation, request);
    }

    @Transactional(readOnly = true)
    public ConversationContext context(String token) {
        FeedbackRequest request = requestService.requireValidToken(token, false);
        FeedbackConversation conversation = conversationRepository.findByFeedbackRequestId(request.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Start the feedback conversation first."));
        var messages = messageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId()).stream()
                .map(message -> new ContextMessage(message.getSenderType().name(), message.getMessageType().name(),
                        message.getContent()))
                .toList();
        long questionsAsked = messageRepository.countByConversationIdAndSenderTypeAndMessageType(
                conversation.getId(), ConversationSenderType.AI, ConversationMessageType.QUESTION);
        return new ConversationContext(request.getBusiness().getName(), messages, questionsAsked,
                activeQuestions(request));
    }

    public List<FeedbackQuestionResponse> activeQuestions(FeedbackRequest request) {
        return questionRepository.findAllByBusinessIdAndActiveTrueOrderByDisplayOrderAscIdAsc(
                        request.getBusiness().getId()).stream()
                .map(this::questionResponse)
                .toList();
    }

    public FeedbackConversation requireActiveConversation(FeedbackRequest request) {
        FeedbackConversation conversation = conversationRepository.findByFeedbackRequestIdForUpdate(request.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Start the feedback conversation first."));
        if (conversation.getStatus() != FeedbackConversationStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "This feedback conversation is no longer active.");
        }
        return conversation;
    }

    public void saveMessage(FeedbackConversation conversation, ConversationSenderType sender,
                            ConversationMessageType type, String content) {
        saveMessage(conversation, sender, type, content, null);
    }

    private void saveMessage(FeedbackConversation conversation, ConversationSenderType sender,
                             ConversationMessageType type, String content, String idempotencyKey) {
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setSenderType(sender);
        message.setMessageType(type);
        message.setContent(content);
        message.setIdempotencyKey(idempotencyKey);
        messageRepository.save(message);
    }

    private String hashMessage(String content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Feedback message idempotency is unavailable.", exception);
        }
    }

    private FeedbackConversation createConversation(FeedbackRequest request) {
        FeedbackConversation conversation = new FeedbackConversation();
        conversation.setBusiness(request.getBusiness());
        conversation.setFeedbackRequest(request);
        conversation.setStatus(FeedbackConversationStatus.IN_PROGRESS);
        conversation.setStartedAt(clock.instant());
        return conversationRepository.save(conversation);
    }

    private FeedbackConversationResponse response(FeedbackConversation conversation, FeedbackRequest request) {
        List<ConversationMessageResponse> messages =
                messageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId()).stream()
                        .map(message -> new ConversationMessageResponse(message.getSenderType(),
                                message.getMessageType(), message.getContent(), message.getCreatedAt()))
                        .toList();
        return new FeedbackConversationResponse(conversation.getStatus(), conversation.getStartedAt(),
                conversation.getCompletedAt(), messages, activeQuestions(request));
    }

    private FeedbackQuestionResponse questionResponse(FeedbackQuestion question) {
        return new FeedbackQuestionResponse(question.getId(), question.getQuestionText(),
                question.getQuestionType(), question.isRequired(), question.isActive(),
                question.getDisplayOrder(), question.getCreatedAt(), question.getUpdatedAt());
    }

    public record CustomerMessageResult(FeedbackConversationResponse response, boolean newlyAccepted) {
    }

    public record ContextMessage(String sender, String type, String content) {
    }

    public record ConversationContext(String businessName, List<ContextMessage> messages,
                                     long followUpCount, List<FeedbackQuestionResponse> questions) {
    }
}
