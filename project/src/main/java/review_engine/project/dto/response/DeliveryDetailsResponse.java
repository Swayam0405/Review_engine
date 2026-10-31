package review_engine.project.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimum protected data required by an internal delivery workflow.
 */
public record DeliveryDetailsResponse(
        UUID eventId,
        UUID feedbackRequestId,
        Instant scheduledAt,
        String businessName,
        String customerPhone,
        String feedbackLink,
        String invitationText) {
}
