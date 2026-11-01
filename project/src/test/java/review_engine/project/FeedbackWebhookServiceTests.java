package review_engine.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import review_engine.project.dto.request.DeliveryCallbackRequest;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.repository.FeedbackRequestRepository;
import review_engine.project.repository.FeedbackWebhookEventRepository;
import review_engine.project.service.FeedbackWebhookService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackWebhookServiceTests {
    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");
    private final UUID requestId = UUID.randomUUID();
    private final UUID deliveryEventId = UUID.randomUUID();
    private FeedbackRequest request;
    private FeedbackWebhookEventRepository eventRepository;
    private FeedbackWebhookService service;

    @BeforeEach
    void setUp() {
        FeedbackRequestRepository requestRepository = mock(FeedbackRequestRepository.class);
        eventRepository = mock(FeedbackWebhookEventRepository.class);
        request = new FeedbackRequest();
        request.setId(requestId);
        request.setStatus(FeedbackRequestStatus.SCHEDULED);
        request.setDeliveryEventId(deliveryEventId);
        when(requestRepository.lockById(requestId)).thenReturn(Optional.of(request));
        when(eventRepository.existsByEventId(any())).thenReturn(false);
        service = new FeedbackWebhookService(requestRepository, eventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void progressesDeliveryStateAndIgnoresOutOfOrderCallbacks() {
        Instant sentAt = NOW.minusSeconds(30);
        assertTrue(service.accept(callback("sent-1", DeliveryCallbackRequest.DeliveryStatus.SENT, sentAt)));
        assertEquals(FeedbackRequestStatus.SENT, request.getStatus());
        assertEquals("provider-1", request.getProviderMessageId());

        assertTrue(service.accept(callback("delivered-old", DeliveryCallbackRequest.DeliveryStatus.DELIVERED,
                sentAt.minusSeconds(1))));
        assertEquals(FeedbackRequestStatus.SENT, request.getStatus());

        Instant deliveredAt = NOW.minusSeconds(10);
        assertTrue(service.accept(callback("delivered-1", DeliveryCallbackRequest.DeliveryStatus.DELIVERED,
                deliveredAt)));
        assertEquals(FeedbackRequestStatus.DELIVERED, request.getStatus());
        assertEquals(deliveredAt, request.getDeliveredAt());

        when(eventRepository.existsByEventId("delivered-1")).thenReturn(true);
        assertFalse(service.accept(callback("delivered-1", DeliveryCallbackRequest.DeliveryStatus.FAILED,
                NOW.minusSeconds(5))));
        assertEquals(FeedbackRequestStatus.DELIVERED, request.getStatus());
    }

    @Test
    void ignoresCallbacksFromSupersededDeliveryAttempts() {
        DeliveryCallbackRequest staleCallback = new DeliveryCallbackRequest(
                "old-attempt", requestId, UUID.randomUUID(), DeliveryCallbackRequest.DeliveryStatus.SENT,
                "old-provider-id", NOW.minusSeconds(30), null);

        assertFalse(service.accept(staleCallback));
        assertEquals(FeedbackRequestStatus.SCHEDULED, request.getStatus());
        assertNull(request.getProviderMessageId());
    }

    private DeliveryCallbackRequest callback(String eventId, DeliveryCallbackRequest.DeliveryStatus status,
                                             Instant eventTimestamp) {
        return new DeliveryCallbackRequest(eventId, requestId, deliveryEventId, status,
                "provider-1", eventTimestamp, null);
    }
}
