package review_engine.project.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.GoogleReviewQrService;

import java.util.UUID;

/**
 * Returns a private, on-demand PNG QR code for the authenticated owner's configured review URL.
 */
@RestController
public class GoogleReviewQrController {
    private final BusinessScopeService businessScope;
    private final GoogleReviewQrService qrService;

    public GoogleReviewQrController(BusinessScopeService businessScope, GoogleReviewQrService qrService) {
        this.businessScope = businessScope;
        this.qrService = qrService;
    }

    @GetMapping("/api/business/me/google-review-qr")
    public ResponseEntity<byte[]> get(Authentication authentication) {
        UUID ownerId = businessScope.authenticatedUserId(authentication);
        byte[] image = qrService.generate(businessScope.requireBusiness(ownerId));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(image.length))
                .body(image);
    }
}
