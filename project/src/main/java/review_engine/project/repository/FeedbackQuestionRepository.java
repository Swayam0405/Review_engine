package review_engine.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import review_engine.project.entity.FeedbackQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Question access scoped to a business for owner and public feedback flows.
 */
public interface FeedbackQuestionRepository extends JpaRepository<FeedbackQuestion, UUID> {
    Optional<FeedbackQuestion> findByIdAndBusinessId(UUID id, UUID businessId);

    Page<FeedbackQuestion> findAllByBusinessId(UUID businessId, Pageable pageable);

    Page<FeedbackQuestion> findAllByBusinessIdAndActive(UUID businessId, boolean active, Pageable pageable);

    List<FeedbackQuestion> findAllByBusinessIdAndActiveTrueOrderByDisplayOrderAscIdAsc(UUID businessId);
}
