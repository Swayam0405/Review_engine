package review_engine.project;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;
import review_engine.project.entity.Business;
import review_engine.project.entity.FeedbackConversation;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.entity.FeedbackOutboxEvent;
import review_engine.project.repository.FeedbackAnswerRepository;
import review_engine.project.repository.FeedbackOutboxRepository;
import review_engine.project.repository.FeedbackQuestionRepository;
import review_engine.project.repository.FeedbackRepository;
import review_engine.project.service.FeedbackConversationService;
import review_engine.project.service.FeedbackRequestService;
import review_engine.project.service.FeedbackSubmissionService;
import review_engine.project.service.GoogleReviewQrService;
import review_engine.project.service.GoogleReviewUrlValidator;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleReviewFlowTests {
    private static final String GOOGLE_REVIEW_URL = "https://g.page/r/example/review";

    @Test
    void acceptsSupportedHttpsGoogleReviewDestinationsAndRejectsUnsafeOrUnrelatedUrls() {
        assertTrue(GoogleReviewUrlValidator.isValid(GOOGLE_REVIEW_URL));
        assertTrue(GoogleReviewUrlValidator.isValid("https://search.google.com/local/writereview?placeid=abc"));
        assertTrue(GoogleReviewUrlValidator.isValid("https://maps.app.goo.gl/example"));
        assertFalse(GoogleReviewUrlValidator.isValid("http://g.page/r/example/review"));
        assertFalse(GoogleReviewUrlValidator.isValid("javascript:alert(1)"));
        assertFalse(GoogleReviewUrlValidator.isValid("https://evilgoogle.com/maps/place/cafe"));
        assertFalse(GoogleReviewUrlValidator.isValid("https://example.com/review"));
    }

    @Test
    void generatedQrEncodesOnlyTheConfiguredReviewUrl() throws Exception {
        Business business = new Business();
        business.setGoogleReviewUrl(GOOGLE_REVIEW_URL);

        byte[] png = new GoogleReviewQrService().generate(business);
        var image = ImageIO.read(new ByteArrayInputStream(png));
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();

        assertNotNull(image);
        assertEquals(GOOGLE_REVIEW_URL, decoded);
    }

    @Test
    void negativeFeedbackCompletesAndStillReturnsConfiguredReviewUrlWithAnalysisQueued() {
        var result = submit(1, GOOGLE_REVIEW_URL);

        assertTrue(result.feedbackCompleted());
        assertTrue(result.conversationCompleted());
        assertTrue(result.googleReviewAvailable());
        assertEquals(GOOGLE_REVIEW_URL, result.googleReviewUrl());
        assertNotNull(result.reviewInvitationMessage());
    }

    @Test
    void positiveFeedbackAlsoReturnsConfiguredReviewUrlWithoutWaitingForAnalysis() {
        var result = submit(5, GOOGLE_REVIEW_URL);

        assertTrue(result.received());
        assertTrue(result.googleReviewAvailable());
        assertEquals(GOOGLE_REVIEW_URL, result.googleReviewUrl());
    }

    @Test
    void missingReviewUrlDoesNotPreventFeedbackCompletion() {
        var result = submit(1, null);

        assertTrue(result.feedbackCompleted());
        assertFalse(result.googleReviewAvailable());
        assertNull(result.googleReviewUrl());
        assertNull(result.reviewInvitationMessage());
    }

    private review_engine.project.dto.response.FeedbackConfirmationResponse submit(int rating, String reviewUrl) {
        FeedbackRequestService requests = mock(FeedbackRequestService.class);
        FeedbackConversationService conversations = mock(FeedbackConversationService.class);
        FeedbackQuestionRepository questions = mock(FeedbackQuestionRepository.class);
        FeedbackRepository feedback = mock(FeedbackRepository.class);
        FeedbackAnswerRepository answers = mock(FeedbackAnswerRepository.class);
        FeedbackOutboxRepository outbox = mock(FeedbackOutboxRepository.class);
        FeedbackRequest request = new FeedbackRequest();
        request.setId(UUID.randomUUID());
        Business business = new Business();
        business.setId(UUID.randomUUID());
        business.setGoogleReviewUrl(reviewUrl);
        request.setBusiness(business);
        FeedbackConversation conversation = new FeedbackConversation();
        when(requests.requireValidToken("opaque-token", true)).thenReturn(request);
        when(feedback.existsByFeedbackRequestId(request.getId())).thenReturn(false);
        when(conversations.requireActiveConversation(request)).thenReturn(conversation);
        when(questions.findAllByBusinessIdAndActiveTrueOrderByDisplayOrderAscIdAsc(business.getId()))
                .thenReturn(List.of());
        when(feedback.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outbox.save(any(FeedbackOutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var service = new FeedbackSubmissionService(requests, conversations, questions, feedback, answers, outbox,
                Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
        var result = service.submit("opaque-token",
                new review_engine.project.dto.request.FeedbackSubmissionRequest(rating, null, List.of()));

        verify(outbox).save(any(FeedbackOutboxEvent.class));
        return result;
    }
}
