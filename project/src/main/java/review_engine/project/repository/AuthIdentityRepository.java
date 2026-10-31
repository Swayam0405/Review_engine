package review_engine.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import review_engine.project.entity.AuthIdentity;
import review_engine.project.enums.AuthProvider;

import java.util.Optional;
import java.util.UUID;

/**
 * Database access for identities, addressed by their provider-specific subject.
 */
public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {
    Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    Optional<AuthIdentity> findByUserIdAndProvider(UUID userId, AuthProvider provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AuthIdentity i where i.provider = :provider and i.providerSubject = :subject")
    Optional<AuthIdentity> findByProviderAndProviderSubjectForUpdate(
            @Param("provider") AuthProvider provider, @Param("subject") String subject);

    boolean existsByUserIdAndVerifiedAtIsNotNull(UUID userId);
}
