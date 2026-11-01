package review_engine.project.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer data visible to the authenticated owner; internal cryptographic fields are omitted.
 *
 * @param id customer identifier
 * @param name customer name
 * @param phone decrypted E.164 number visible only inside the owner's authenticated scope
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record CustomerResponse(UUID id, String name, String phone, Instant createdAt, Instant updatedAt) {
}
