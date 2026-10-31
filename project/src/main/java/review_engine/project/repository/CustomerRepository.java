package review_engine.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import review_engine.project.entity.Customer;

import java.util.Optional;
import java.util.UUID;

/**
 * Customer persistence operations that always include the business scope.
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<Customer> findByBusinessIdAndPhoneLookupHash(UUID businessId, String phoneLookupHash);

    Page<Customer> findAllByBusinessId(UUID businessId, Pageable pageable);

    Page<Customer> findAllByBusinessIdAndNameContainingIgnoreCase(
            UUID businessId, String name, Pageable pageable);
}
