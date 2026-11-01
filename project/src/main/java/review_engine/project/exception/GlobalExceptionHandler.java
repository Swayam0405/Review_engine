package review_engine.project.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.ai.AIProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps application and request-validation failures to safe API response envelopes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Converts a deliberate application failure into its declared safe response.
     *
     * @param exception expected application error
     * @return response with the exception's status and safe message
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(AIProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderFailure(AIProviderException exception) {
        LOGGER.warn("AI provider operation failed: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("AI provider could not produce a valid response."));
    }

    /**
     * Reports the first invalid request field without echoing the submitted value.
     *
     * @param exception validation result from request binding
     * @return bad-request response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "Request validation failed."
                : "Invalid value for field: " + fieldError.getField() + ".";
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    /**
     * Converts malformed JSON, UUIDs, timestamps, and query parameter values into safe client errors.
     *
     * @param exception request parsing or type-conversion failure
     * @return bad-request response without input values or parser details
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleRequestParsing(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Request contains an invalid value or format."));
    }

    /**
     * Hides SQL and constraint details while reporting a data conflict.
     *
     * @param exception persistence constraint failure
     * @return conflict response
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("The request conflicts with existing account or business data."));
    }

    /**
     * Logs an unexpected server failure internally and returns a sanitized response.
     *
     * @param exception unhandled application exception
     * @return generic internal-server-error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled API exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected server error occurred."));
    }
}
