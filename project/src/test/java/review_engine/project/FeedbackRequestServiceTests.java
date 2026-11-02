package review_engine.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import review_engine.project.entity.Business;
import review_engine.project.entity.Customer;
import review_engine.project.entity.Feedback;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.entity.TransactionEntity;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.repository.FeedbackOutboxRepository;
import review_engine.project.repository.FeedbackRepository;
import review_engine.project.repository.FeedbackRequestRepository;
import review_engine.project.repository.TransactionRepository;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.FeedbackRequestService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackRequestServiceTests {
    private FeedbackRequestRepository requests;
    private FeedbackRepository feedback;
    private BusinessScopeService businessScope;
    private FeedbackRequestService service;

    @BeforeEach
    void setUp() {
        requests = mock(FeedbackRequestRepository.class);
        feedback = mock(FeedbackRepository.class);
        businessScope = mock(BusinessScopeService.class);
        service = new FeedbackRequestService(
                requests,
                feedback,
                mock(FeedbackOutboxRepository.class),
                mock(TransactionRepository.class),
                businessScope,
                mock(review_engine.project.service.FeedbackTokenService.class),
                mock(review_engine.project.config.FeedbackProperties.class),
                Clock.systemUTC());
    }

    @Test
    void ownerRequestListMapsTheActualFeedbackIdInOneBatch() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        Business business = new Business();
        business.setId(businessId);
        FeedbackRequest request = request(requestId, business);
        Feedback submittedFeedback = new Feedback();
        submittedFeedback.setId(feedbackId);
        submittedFeedback.setFeedbackRequest(request);
        when(businessScope.requireBusiness(ownerId)).thenReturn(business);
        when(requests.findAllByBusinessId(eq(businessId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request)));
        when(feedback.findAllByFeedbackRequestIdInAndBusinessId(List.of(requestId), businessId))
                .thenReturn(List.of(submittedFeedback));

        var response = service.list(ownerId, null, 0, 20);

        assertEquals(feedbackId, response.content().getFirst().feedbackId());
        verify(feedback).findAllByFeedbackRequestIdInAndBusinessId(List.of(requestId), businessId);
    }

    private FeedbackRequest request(UUID requestId, Business business) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        FeedbackRequest request = new FeedbackRequest();
        request.setId(requestId);
        request.setBusiness(business);
        request.setTransaction(transaction);
        request.setCustomer(customer);
        request.setStatus(FeedbackRequestStatus.COMPLETED);
        request.setScheduledAt(Instant.now());
        request.setExpiresAt(Instant.now().plusSeconds(3600));
        request.setCreatedAt(Instant.now());
        return request;
    }
}
