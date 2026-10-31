package review_engine.project.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Client-editable values for creating a business-scoped customer.
 *
 * @param name customer's display name
 * @param phone phone number to normalize and encrypt
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateCustomerRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[^\\p{Cc}]*$") String name,
        @NotBlank @Size(max = 64) String phone) {
}
