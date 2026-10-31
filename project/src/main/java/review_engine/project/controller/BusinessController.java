package review_engine.project.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.BusinessRequest;
import review_engine.project.dto.request.GoogleReviewUrlRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.BusinessResponse;
import review_engine.project.service.BusinessService;

import java.util.UUID;

/**
 * Authenticated HTTP endpoints for business onboarding and owner-scoped profile management.
 */
@RestController
@RequestMapping("/api/business")
public class BusinessController {
    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    /**
     * Creates the authenticated user's business profile; ownership is never client-supplied.
     *
     * @param authentication validated Spring Security JWT principal
     * @param request profile data
     * @return created business profile
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusinessResponse> create(Authentication authentication,
                                                @Valid @RequestBody BusinessRequest request) {
        return ApiResponse.success("Business profile created.", businessService.create(userId(authentication), request));
    }

    /**
     * Returns the business profile belonging to the authenticated user.
     *
     * @param authentication validated Spring Security JWT principal
     * @return owner's profile or a safe onboarding-required 404
     */
    @GetMapping("/me")
    public ApiResponse<BusinessResponse> getMine(Authentication authentication) {
        return ApiResponse.success("Business profile retrieved.", businessService.getMine(userId(authentication)));
    }

    /**
     * Updates editable profile fields without accepting owner or business identifiers.
     *
     * @param authentication validated Spring Security JWT principal
     * @param request replacement profile fields
     * @return updated business profile
     */
    @PutMapping("/me")
    public ApiResponse<BusinessResponse> update(Authentication authentication,
                                                @Valid @RequestBody BusinessRequest request) {
        return ApiResponse.success("Business profile updated.",
                businessService.update(userId(authentication), request));
    }

    /**
     * Changes only the owner's Google review URL.
     *
     * @param authentication validated Spring Security JWT principal
     * @param request new HTTPS URL
     * @return updated business profile
     */
    @PatchMapping("/me/google-review-url")
    public ApiResponse<BusinessResponse> updateGoogleReviewUrl(Authentication authentication,
                                                               @Valid @RequestBody GoogleReviewUrlRequest request) {
        return ApiResponse.success("Google review URL updated.",
                businessService.updateGoogleReviewUrl(userId(authentication), request.googleReviewUrl()));
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
