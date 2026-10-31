package review_engine.project.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.DeliveryCallbackRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.DeliveryDetailsResponse;
import review_engine.project.exception.ApiException;
import review_engine.project.service.FeedbackDeliveryDetailsService;
import review_engine.project.service.FeedbackWebhookService;
import review_engine.project.service.N8nIntegrationService;

import java.util.UUID;

/**
 * Secret-authenticated internal endpoints for n8n delivery data and provider callbacks.
 */
@RestController
@RequestMapping("/api/internal")
public class FeedbackInternalController {
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    public static final String CALLBACK_SECRET_HEADER = "X-Webhook-Secret";
    private final N8nIntegrationService n8n;
    private final FeedbackDeliveryDetailsService deliveryDetails;
    private final FeedbackWebhookService webhookService;

    public FeedbackInternalController(N8nIntegrationService n8n,
                                      FeedbackDeliveryDetailsService deliveryDetails,
                                      FeedbackWebhookService webhookService) {
        this.n8n = n8n;
        this.deliveryDetails = deliveryDetails;
        this.webhookService = webhookService;
    }

    @GetMapping("/feedback-requests/{requestId}/delivery")
    public ApiResponse<DeliveryDetailsResponse> delivery(
            @PathVariable UUID requestId,
            @RequestParam UUID eventId,
            @RequestHeader(name = INTERNAL_KEY_HEADER, required = false) String secret) {
        requireSecret(n8n.matchesInternalSecret(secret));
        if (!n8n.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Delivery integration is disabled.");
        }
        return ApiResponse.success("Delivery details retrieved.", deliveryDetails.get(requestId, eventId));
    }

    @PostMapping("/webhooks/whatsapp/delivery")
    public ApiResponse<Boolean> deliveryCallback(
            @RequestHeader(name = CALLBACK_SECRET_HEADER, required = false) String secret,
            @Valid @RequestBody DeliveryCallbackRequest request) {
        requireSecret(n8n.matchesCallbackSecret(secret));
        if (!n8n.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Delivery integration is disabled.");
        }
        return ApiResponse.success("Delivery callback processed.", webhookService.accept(request));
    }

    private void requireSecret(boolean valid) {
        if (!valid) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Internal authentication is required.");
        }
    }
}
