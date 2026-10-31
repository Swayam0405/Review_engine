package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.request.BusinessRequest;
import review_engine.project.dto.response.BusinessResponse;
import review_engine.project.entity.Business;
import review_engine.project.entity.User;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.BusinessRepository;
import review_engine.project.repository.UserRepository;

import java.net.URI;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Manages business onboarding and profile updates using the authenticated owner scope.
 */
@Service
public class BusinessService {
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    public BusinessService(BusinessRepository businessRepository, UserRepository userRepository) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates the single business profile allowed for the authenticated account.
     *
     * @param ownerId UUID derived from the validated JWT principal
     * @param request validated business profile fields
     * @return persisted business profile
     * @throws ApiException if the owner does not exist or already has a business
     */
    @Transactional
    public BusinessResponse create(UUID ownerId, BusinessRequest request) {
        if (businessRepository.existsByOwnerId(ownerId)) {
            throw new ApiException(HttpStatus.CONFLICT, "A business profile already exists for this account.");
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required."));
        Business business = new Business();
        business.setOwner(owner);
        apply(business, request);
        return toResponse(businessRepository.save(business));
    }

    /**
     * Retrieves only the business owned by the authenticated account.
     *
     * @param ownerId UUID derived from the validated JWT principal
     * @return owner's business profile
     * @throws ApiException when onboarding has not yet been completed
     */
    @Transactional(readOnly = true)
    public BusinessResponse getMine(UUID ownerId) {
        return businessRepository.findByOwnerId(ownerId).map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business onboarding is required."));
    }

    /**
     * Replaces the editable business profile fields without changing owner or identity.
     *
     * @param ownerId UUID derived from the validated JWT principal
     * @param request replacement profile fields
     * @return updated business profile
     */
    @Transactional
    public BusinessResponse update(UUID ownerId, BusinessRequest request) {
        Business business = findOwnedBusiness(ownerId);
        apply(business, request);
        return toResponse(business);
    }

    /**
     * Updates only the Google review URL for the authenticated owner's profile.
     *
     * @param ownerId UUID derived from the validated JWT principal
     * @param googleReviewUrl requested HTTPS URL
     * @return updated profile
     */
    @Transactional
    public BusinessResponse updateGoogleReviewUrl(UUID ownerId, String googleReviewUrl) {
        Business business = findOwnedBusiness(ownerId);
        business.setGoogleReviewUrl(validateReviewUrl(googleReviewUrl));
        return toResponse(business);
    }

    private Business findOwnedBusiness(UUID ownerId) {
        return businessRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business onboarding is required."));
    }

    private void apply(Business business, BusinessRequest request) {
        business.setName(request.name().trim());
        business.setCategory(request.category().trim());
        business.setAddress(request.address().trim());
        business.setTimezone(validateTimezone(request.timezone()));
        business.setGoogleReviewUrl(validateReviewUrl(request.googleReviewUrl()));
    }

    private String validateTimezone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid IANA timezone is required.");
        }
    }

    private String validateReviewUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Unsafe URL.");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Google review URL must be a valid HTTPS URL.");
        }
    }

    private BusinessResponse toResponse(Business business) {
        return new BusinessResponse(business.getId(), business.getName(), business.getCategory(),
                business.getAddress(), business.getTimezone(), business.getGoogleReviewUrl(),
                business.getCreatedAt(), business.getUpdatedAt());
    }
}
