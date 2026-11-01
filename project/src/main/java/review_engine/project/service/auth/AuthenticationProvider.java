package review_engine.project.service.auth;

import review_engine.project.entity.User;
import review_engine.project.enums.AuthProvider;

/**
 * Provider adapter that authenticates a credential and resolves an application user.
 */
public interface AuthenticationProvider {
    AuthProvider provider();

    User authenticate(String subject, String credential);
}
