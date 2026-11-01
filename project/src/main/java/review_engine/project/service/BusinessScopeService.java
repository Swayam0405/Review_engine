package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import review_engine.project.entity.Business;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.BusinessRepository;

import java.util.UUID;

/**
 * Resolves the business boundary from the authenticated application-user identity.
 */
@Service
public class BusinessScopeService {
    private final BusinessRepository businessRepository;

    public BusinessScopeService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    /**
     * Parses the application UUID from the validated JWT subject.
     */
    public UUID authenticatedUserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
    }

    /**
     * Resolves the one business owned by the authenticated account.
     */
    public Business requireBusiness(UUID ownerId) {
        return businessRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business onboarding is required."));
    }
}
