package review_engine.project.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.CreateCustomerRequest;
import review_engine.project.dto.request.UpdateCustomerRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.CustomerResponse;
import review_engine.project.dto.response.CustomerSummaryResponse;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.CustomerService;
import review_engine.project.util.PageRequests;

import java.util.UUID;

/**
 * Authenticated customer endpoints; every operation derives business ownership from JWT identity.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;
    private final BusinessScopeService businessScopeService;

    public CustomerController(CustomerService customerService, BusinessScopeService businessScopeService) {
        this.customerService = customerService;
        this.businessScopeService = businessScopeService;
    }

    /**
     * Creates a customer for the authenticated owner's business.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerResponse> create(Authentication authentication,
                                                @Valid @RequestBody CreateCustomerRequest request) {
        UUID ownerId = businessScopeService.authenticatedUserId(authentication);
        return ApiResponse.success("Customer created successfully.", customerService.create(ownerId, request));
    }

    /**
     * Returns a database-paginated, optionally name-filtered owner-scoped customer page.
     */
    @GetMapping
    public ApiResponse<PageResponse<CustomerSummaryResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String name) {
        UUID ownerId = businessScopeService.authenticatedUserId(authentication);
        return ApiResponse.success("Customers retrieved successfully.",
                customerService.list(ownerId, name, PageRequests.customers(page, size, sort)));
    }

    /**
     * Retrieves a customer only from the authenticated owner's business.
     */
    @GetMapping("/{customerId}")
    public ApiResponse<CustomerResponse> get(Authentication authentication, @PathVariable UUID customerId) {
        UUID ownerId = businessScopeService.authenticatedUserId(authentication);
        return ApiResponse.success("Customer retrieved successfully.", customerService.get(ownerId, customerId));
    }

    /**
     * Updates the editable fields of an owner-scoped customer.
     */
    @PutMapping("/{customerId}")
    public ApiResponse<CustomerResponse> update(Authentication authentication, @PathVariable UUID customerId,
                                                @Valid @RequestBody UpdateCustomerRequest request) {
        UUID ownerId = businessScopeService.authenticatedUserId(authentication);
        return ApiResponse.success("Customer updated successfully.",
                customerService.update(ownerId, customerId, request));
    }
}
