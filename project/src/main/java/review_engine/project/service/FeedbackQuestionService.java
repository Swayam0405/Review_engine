package review_engine.project.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.request.FeedbackQuestionRequest;
import review_engine.project.dto.response.FeedbackQuestionResponse;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.entity.Business;
import review_engine.project.entity.FeedbackQuestion;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.FeedbackAnswerRepository;
import review_engine.project.repository.FeedbackQuestionRepository;

import java.util.UUID;

/**
 * Manages business-owned question templates while preserving referenced question history.
 */
@Service
public class FeedbackQuestionService {
    private final FeedbackQuestionRepository repository;
    private final FeedbackAnswerRepository answerRepository;
    private final BusinessScopeService businessScope;

    public FeedbackQuestionService(FeedbackQuestionRepository repository, FeedbackAnswerRepository answerRepository,
                                   BusinessScopeService businessScope) {
        this.repository = repository;
        this.answerRepository = answerRepository;
        this.businessScope = businessScope;
    }

    @Transactional
    public FeedbackQuestionResponse create(UUID ownerId, FeedbackQuestionRequest request) {
        Business business = businessScope.requireBusiness(ownerId);
        FeedbackQuestion question = new FeedbackQuestion();
        question.setBusiness(business);
        apply(question, request);
        return response(repository.save(question));
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedbackQuestionResponse> list(UUID ownerId, Boolean active, int page, int size) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        Pageable pageable = pageable(page, size);
        Page<FeedbackQuestion> questions = active == null
                ? repository.findAllByBusinessId(businessId, pageable)
                : repository.findAllByBusinessIdAndActive(businessId, active, pageable);
        return PageResponse.from(questions.map(this::response));
    }

    @Transactional(readOnly = true)
    public FeedbackQuestionResponse get(UUID ownerId, UUID questionId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        return response(find(questionId, businessId));
    }

    @Transactional
    public FeedbackQuestionResponse update(UUID ownerId, UUID questionId, FeedbackQuestionRequest request) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        FeedbackQuestion question = find(questionId, businessId);
        if (answerRepository.existsByQuestionId(questionId)
                && (!question.getQuestionText().equals(request.questionText().strip())
                || question.getQuestionType() != request.questionType())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "A question with submitted answers cannot change its text or answer type.");
        }
        apply(question, request);
        return response(question);
    }

    @Transactional
    public FeedbackQuestionResponse setActive(UUID ownerId, UUID questionId, boolean active) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        FeedbackQuestion question = find(questionId, businessId);
        question.setActive(active);
        return response(question);
    }

    private FeedbackQuestion find(UUID questionId, UUID businessId) {
        return repository.findByIdAndBusinessId(questionId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feedback question was not found."));
    }

    private Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayOrder").and(Sort.by("id")));
    }

    private void apply(FeedbackQuestion question, FeedbackQuestionRequest request) {
        String text = request.questionText().strip();
        if (text.isBlank() || text.length() > 1000 || text.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Question text must contain 1 to 1000 valid characters.");
        }
        if (request.displayOrder() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Display order cannot be negative.");
        }
        question.setQuestionText(text);
        question.setQuestionType(request.questionType());
        question.setRequired(request.required());
        question.setDisplayOrder(request.displayOrder());
        question.setActive(request.active());
    }

    private FeedbackQuestionResponse response(FeedbackQuestion question) {
        return new FeedbackQuestionResponse(question.getId(), question.getQuestionText(),
                question.getQuestionType(), question.isRequired(), question.isActive(),
                question.getDisplayOrder(), question.getCreatedAt(), question.getUpdatedAt());
    }
}
