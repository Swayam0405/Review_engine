package review_engine.project.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Replacement customer name and phone; ownership and identity are not editable.
 *
 * @param name customer's display name
 * @param phone phone number to normalize and encrypt
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateCustomerRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[^\\p{Cc}]*$") String name,
        @NotBlank @Size(max = 64) String phone) {
}
