package review_engine.project.dto.response;

import java.time.Instant;

/**
 * Standard success and error envelope returned by the backend API.
 *
 * @param success whether the operation completed successfully
 * @param message safe user-facing summary
 * @param data operation result, when present
 * @param timestamp response creation time in UTC
 * @param <T> payload type
 */
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp) {
    /**
     * Creates a success envelope with the current UTC response timestamp.
     *
     * @param message safe operation summary
     * @param data typed response payload
     * @param <T> payload type
     * @return successful API response
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    /**
     * Creates a failure envelope without exposing implementation details.
     *
     * @param message safe error summary
     * @return failed API response with no data
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
