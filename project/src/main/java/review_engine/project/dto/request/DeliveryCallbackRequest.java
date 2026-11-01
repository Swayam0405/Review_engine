package review_engine.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Authenticated delivery event callback from the n8n/WhatsApp integration.
 */
public record DeliveryCallbackRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+") String eventId,
        @NotNull UUID feedbackRequestId,
        @NotNull UUID deliveryEventId,
        @NotNull DeliveryStatus status,
        @Size(max = 255) String providerMessageId,
        @NotNull Instant eventTimestamp,
        @Size(max = 1000) String failureReason) {

    /**
     * Provider-facing delivery outcomes.
     */
    public enum DeliveryStatus {
        SENT,
        DELIVERED,
        FAILED
    }
}
