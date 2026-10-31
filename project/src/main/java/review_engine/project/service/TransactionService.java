package review_engine.project.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.request.CreateTransactionRequest;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.dto.response.TransactionResponse;
import review_engine.project.entity.Business;
import review_engine.project.entity.Customer;
import review_engine.project.entity.TransactionEntity;
import review_engine.project.enums.TransactionStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.CustomerRepository;
import review_engine.project.repository.TransactionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates and retrieves completed transactions without exposing mutation or deletion workflows.
 */
@Service
public class TransactionService {
    private static final long FUTURE_CLOCK_SKEW_SECONDS = 300;
    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final BusinessScopeService businessScopeService;
    private final Clock clock;

    public TransactionService(TransactionRepository transactionRepository, CustomerRepository customerRepository,
                              BusinessScopeService businessScopeService, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.businessScopeService = businessScopeService;
        this.clock = clock;
    }

    /**
     * Records an already completed transaction; the server, not the client, assigns its status.
     */
    @Transactional
    public TransactionResponse create(UUID ownerId, CreateTransactionRequest request) {
        Business business = businessScopeService.requireBusiness(ownerId);
        Customer customer = customerRepository.findByIdAndBusinessId(request.customerId(), business.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer was not found."));
        String referenceCode = normalizeReferenceCode(request.referenceCode());
        if (referenceCode != null
                && transactionRepository.existsByBusinessIdAndReferenceCode(business.getId(), referenceCode)) {
            throw duplicateReference();
        }
        Instant completedAt = request.completedAt() == null
                ? clock.instant() : request.completedAt().toInstant();
        validateCompletionTime(completedAt);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setBusiness(business);
        transaction.setCustomer(customer);
        transaction.setReferenceCode(referenceCode);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(completedAt);
        return toResponse(transactionRepository.save(transaction));
    }

    /**
     * Searches only the owner's transactions and applies filters in the database.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(UUID ownerId, UUID customerId, Instant from, Instant to,
                                                   TransactionStatus status, String referenceSearch,
                                                   Pageable pageable) {
        UUID businessId = businessScopeService.requireBusiness(ownerId).getId();
        validateDateRange(from, to);
        if (status != null && status != TransactionStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only COMPLETED transactions are supported.");
        }
        String reference = referenceSearch == null ? null : referenceSearch.strip();
        if (reference != null && reference.isEmpty()) {
            reference = null;
        }
        if (reference != null && reference.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reference search must not exceed 100 characters.");
        }
        String finalReference = reference;
        Specification<TransactionEntity> specification = (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("business").get("id"), businessId));
            if (customerId != null) {
                predicates.add(builder.equal(root.get("customer").get("id"), customerId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("completedAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("completedAt"), to));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (finalReference != null) {
                predicates.add(builder.like(builder.lower(root.get("referenceCode")),
                        "%" + finalReference.toLowerCase(java.util.Locale.ROOT) + "%"));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<TransactionEntity> transactions = transactionRepository.findAll(specification, pageable);
        return PageResponse.from(transactions.map(this::toResponse));
    }

    /**
     * Retrieves a transaction only when both the transaction and caller resolve to the same business.
     */
    @Transactional(readOnly = true)
    public TransactionResponse get(UUID ownerId, UUID transactionId) {
        UUID businessId = businessScopeService.requireBusiness(ownerId).getId();
        return transactionRepository.findByIdAndBusinessId(transactionId, businessId)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction was not found."));
    }

    /**
     * Validates inclusive date filters before they reach the repository.
     */
    public void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Start date must not be after end date.");
        }
    }

    private void validateCompletionTime(Instant completedAt) {
        if (completedAt.isAfter(clock.instant().plusSeconds(FUTURE_CLOCK_SKEW_SECONDS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Completion time must not be more than five minutes in the future.");
        }
    }

    private String normalizeReferenceCode(String value) {
        if (value == null) {
            return null;
        }
        String reference = value.strip();
        if (reference.isEmpty() || reference.length() > 100
                || reference.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reference code must contain 1 to 100 valid characters.");
        }
        return reference;
    }

    private ApiException duplicateReference() {
        return new ApiException(HttpStatus.CONFLICT, "A transaction with this reference code already exists.");
    }

    private TransactionResponse toResponse(TransactionEntity transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getCustomer().getId(),
                transaction.getCustomer().getName(), transaction.getReferenceCode(), transaction.getStatus(),
                transaction.getCompletedAt(), transaction.getCreatedAt(), transaction.getUpdatedAt());
    }
}
