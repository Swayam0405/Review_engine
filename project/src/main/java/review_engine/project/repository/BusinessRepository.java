package review_engine.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import review_engine.project.entity.Business;

import java.util.Optional;
import java.util.UUID;

/**
 * Owner-scoped business profile access.
 */
public interface BusinessRepository extends JpaRepository<Business, UUID> {
    Optional<Business> findByOwnerId(UUID ownerId);

    boolean existsByOwnerId(UUID ownerId);
}
