package review_engine.project.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Client-editable fields for recording an already completed transaction.
 *
 * @param customerId existing customer UUID
 * @param referenceCode optional business-local reference
 * @param completedAt optional offset-aware completion timestamp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTransactionRequest(
        @NotNull UUID customerId,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String referenceCode,
        OffsetDateTime completedAt) {
}
