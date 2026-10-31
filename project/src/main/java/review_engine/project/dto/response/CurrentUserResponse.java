package review_engine.project.dto.response;

import review_engine.project.enums.AccountStatus;

import java.util.UUID;

/**
 * Public user account fields and current server-side onboarding state.
 */
public record CurrentUserResponse(
        UUID id,
        String name,
        String email,
        AccountStatus accountStatus,
        boolean emailVerified,
        boolean hasBusiness) {
}
