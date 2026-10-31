package review_engine.project.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.CreateTransactionRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.dto.response.TransactionResponse;
import review_engine.project.enums.TransactionStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.TransactionService;
import review_engine.project.util.PageRequests;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Authenticated endpoints for manually recording and viewing completed transactions.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;
    private final BusinessScopeService businessScopeService;

    public TransactionController(TransactionService transactionService, BusinessScopeService businessScopeService) {
        this.transactionService = transactionService;
        this.businessScopeService = businessScopeService;
    }

    /**
     * Records a completed transaction; status and business are assigned by the backend.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(Authentication authentication,
                                                   @Valid @RequestBody CreateTransactionRequest request) {
        UUID ownerId = businessScopeService.authenticatedUserId(authentication);
        return ApiResponse.success("Completed transaction recorded successfully.",
                transactionService.create(ownerId, request));
    }

    /**
     * Lists only this owner's transactions with optional database-side filters.
     */
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "completedAt,desc") String sort,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String referenceCode) {
        UUID ownerId = businessScopeService.authenticatedUserId(authentication);
        TransactionStatus parsedStatus = parseStatus(status);
        return ApiResponse.success("Transactions retrieved successfully.",
                transactionService.list(ownerId, customerId, from == null ? null : from.toInstant(),
                        to == null ? null : to.toInstant(), parsedStatus, referenceCode,
                        PageRequests.transactions(page, size, sort)));
    }

    /**
     * Retrieves a transaction only inside the authenticated owner's business scope.
     */
    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionResponse> get(Authentication authentication,
                                                @PathVariable UUID transactionId) {
        UUID ownerId = businessScopeService.authenticatedUserId(authentication);
        return ApiResponse.success("Transaction retrieved successfully.",
                transactionService.get(ownerId, transactionId));
    }

    private TransactionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            TransactionStatus parsed = TransactionStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
            if (parsed != TransactionStatus.COMPLETED) {
                throw new IllegalArgumentException("Unsupported transaction status.");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only COMPLETED transactions are supported.");
        }
    }
}
