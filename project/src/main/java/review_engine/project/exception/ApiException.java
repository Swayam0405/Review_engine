package review_engine.project.exception;

import org.springframework.http.HttpStatus;

/**
 * An expected application failure with a safe HTTP status and message.
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    /**
     * Creates an expected failure with an explicit HTTP status and safe message.
     *
     * @param status response status
     * @param message user-safe error summary
     */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status used by the global exception handler.
     *
     * @return API response status
     */
    public HttpStatus getStatus() {
        return status;
    }
}
