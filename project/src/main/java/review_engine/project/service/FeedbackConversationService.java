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
        saveMessage(conversation, ConversationSenderType.CUSTOMER, ConversationMessageType.TEXT, normalized);
        return response(conversation, request);
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
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setSenderType(sender);
        message.setMessageType(type);
        message.setContent(content);
        messageRepository.save(message);
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
}
