package review_engine.project.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import review_engine.project.exception.ApiException;

import java.util.Map;

/**
 * Validates requested page bounds and maps public sort names to entity properties.
 */
public final class PageRequests {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> CUSTOMER_SORT_FIELDS = Map.of(
            "name", "name", "createdAt", "createdAt", "updatedAt", "updatedAt");
    private static final Map<String, String> TRANSACTION_SORT_FIELDS = Map.of(
            "completedAt", "completedAt", "createdAt", "createdAt", "referenceCode", "referenceCode");

    private PageRequests() {
    }

    /**
     * Builds a bounded customer page with only public customer sort fields.
     */
    public static Pageable customers(int page, int size, String sort) {
        return pageable(page, size, sort, CUSTOMER_SORT_FIELDS, "createdAt");
    }

    /**
     * Builds a bounded transaction page with only public transaction sort fields.
     */
    public static Pageable transactions(int page, int size, String sort) {
        return pageable(page, size, sort, TRANSACTION_SORT_FIELDS, "completedAt");
    }

    private static Pageable pageable(int page, int size, String sort, Map<String, String> fields,
                                     String defaultField) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100.");
        }
        String[] parts = sort == null || sort.isBlank() ? new String[0] : sort.split(",", -1);
        String requestedField = parts.length == 0 ? defaultField : parts[0].trim();
        String property = fields.get(requestedField);
        if (property == null || parts.length > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported sort field.");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Sort direction must be asc or desc.");
            }
        }
        return PageRequest.of(page, size, Sort.by(direction, property).and(Sort.by("id")));
    }
}
