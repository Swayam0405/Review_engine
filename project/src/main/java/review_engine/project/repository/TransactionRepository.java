package review_engine.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import review_engine.project.entity.TransactionEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Transaction persistence supports business-scoped retrieval and filtering.
 */
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID>,
        JpaSpecificationExecutor<TransactionEntity> {
    Optional<TransactionEntity> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndReferenceCode(UUID businessId, String referenceCode);
}
