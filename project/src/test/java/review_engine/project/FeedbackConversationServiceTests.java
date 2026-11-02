package review_engine.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import review_engine.project.entity.Business;
import review_engine.project.entity.Feedback;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.repository.ConversationMessageRepository;
import review_engine.project.repository.FeedbackConversationRepository;
import review_engine.project.repository.FeedbackQuestionRepository;
import review_engine.project.repository.FeedbackRepository;
import review_engine.project.service.FeedbackConversationService;
import review_engine.project.service.FeedbackRequestService;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackConversationServiceTests {
    private FeedbackRequestService requests;
    private FeedbackQuestionRepository questions;
    private FeedbackRepository feedback;
    private FeedbackConversationService conversations;

    @BeforeEach
    void setUp() {
        requests = mock(FeedbackRequestService.class);
        questions = mock(FeedbackQuestionRepository.class);
        feedback = mock(FeedbackRepository.class);
        conversations = new FeedbackConversationService(
                requests,
                mock(FeedbackConversationRepository.class),
                mock(ConversationMessageRepository.class),
                questions,
                feedback,
                Clock.systemUTC());
    }

    @Test
    void completedLinkReturnsOnlySafeCompletionState() {
        UUID requestId = UUID.randomUUID();
        FeedbackRequest request = request(requestId, FeedbackRequestStatus.COMPLETED);
        Feedback submittedFeedback = new Feedback();
        submittedFeedback.setId(UUID.randomUUID());
        when(requests.requireValidPageToken("opaque-token")).thenReturn(request);
        when(feedback.findByFeedbackRequestIdAndBusinessId(
                requestId, request.getBusiness().getId())).thenReturn(Optional.of(submittedFeedback));

        var page = conversations.validateLink("opaque-token");

        assertEquals("Review Business", page.businessName());
        assertTrue(page.questions().isEmpty());
        assertNotNull(page.completion());
        assertTrue(page.completion().feedbackCompleted());
        assertTrue(page.completion().conversationCompleted());
        assertEquals("Thank you. Your feedback has been received.", page.completion().completionMessage());
        assertNull(page.completion().googleReviewUrl());
        verify(questions, never()).findAllByBusinessIdAndActiveTrueOrderByDisplayOrderAscIdAsc(
                request.getBusiness().getId());
    }

    @Test
    void activeLinkKeepsExistingQuestionResponseAndNoCompletionProjection() {
        FeedbackRequest request = request(UUID.randomUUID(), FeedbackRequestStatus.DELIVERED);
        when(requests.requireValidPageToken("opaque-token")).thenReturn(request);
        when(questions.findAllByBusinessIdAndActiveTrueOrderByDisplayOrderAscIdAsc(
                request.getBusiness().getId())).thenReturn(java.util.List.of());

        var page = conversations.validateLink("opaque-token");

        assertEquals("Review Business", page.businessName());
        assertTrue(page.questions().isEmpty());
        assertNull(page.completion());
        verify(feedback, never()).findByFeedbackRequestIdAndBusinessId(
                request.getId(), request.getBusiness().getId());
    }

    private FeedbackRequest request(UUID id, FeedbackRequestStatus status) {
        Business business = new Business();
        business.setId(UUID.randomUUID());
        business.setName("Review Business");
        FeedbackRequest request = new FeedbackRequest();
        request.setId(id);
        request.setStatus(status);
        request.setBusiness(business);
        request.setExpiresAt(Instant.now().plusSeconds(3600));
        return request;
    }
}
