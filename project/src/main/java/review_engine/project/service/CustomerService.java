package review_engine.project.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.request.CreateCustomerRequest;
import review_engine.project.dto.request.UpdateCustomerRequest;
import review_engine.project.dto.response.CustomerResponse;
import review_engine.project.dto.response.CustomerSummaryResponse;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.entity.Business;
import review_engine.project.entity.Customer;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.CustomerRepository;

import java.util.UUID;
import java.time.Clock;

/**
 * Customer operations scoped to the one business resolved for the authenticated owner.
 */
@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final BusinessScopeService businessScopeService;
    private final PhoneNumberSecurityService phoneSecurity;
    private final Clock clock;

    public CustomerService(CustomerRepository customerRepository, BusinessScopeService businessScopeService,
                           PhoneNumberSecurityService phoneSecurity, Clock clock) {
        this.customerRepository = customerRepository;
        this.businessScopeService = businessScopeService;
        this.phoneSecurity = phoneSecurity;
        this.clock = clock;
    }

    /**
     * Creates a customer after business-scoped duplicate detection and phone protection.
     */
    @Transactional
    public CustomerResponse create(UUID ownerId, CreateCustomerRequest request) {
        Business business = businessScopeService.requireBusiness(ownerId);
        String name = validateName(request.name());
        String phone = phoneSecurity.normalize(request.phone());
        String lookupHash = phoneSecurity.lookupHash(phone);
        if (customerRepository.findByBusinessIdAndPhoneLookupHash(business.getId(), lookupHash).isPresent()) {
            throw duplicatePhone();
        }

        Customer customer = new Customer();
        customer.setBusiness(business);
        customer.setName(name);
        customer.setPhoneLookupHash(lookupHash);
        customer.setPhoneEncrypted(phoneSecurity.encrypt(phone));
        return toResponse(customerRepository.save(customer));
    }

    /**
     * Lists only this business's customers using database-side filtering and pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryResponse> list(UUID ownerId, String nameSearch, Pageable pageable) {
        UUID businessId = businessScopeService.requireBusiness(ownerId).getId();
        if (nameSearch != null && (nameSearch.length() > 120
                || nameSearch.codePoints().anyMatch(Character::isISOControl))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Name search must not exceed 120 valid characters.");
        }
        Page<Customer> customers = nameSearch == null || nameSearch.isBlank()
                ? customerRepository.findAllByBusinessId(businessId, pageable)
                : customerRepository.findAllByBusinessIdAndNameContainingIgnoreCase(
                        businessId, nameSearch.strip(), pageable);
        return PageResponse.from(customers.map(customer -> new CustomerSummaryResponse(
                customer.getId(), customer.getName(), customer.getCreatedAt(), customer.getUpdatedAt())));
    }

    /**
     * Retrieves a customer only when its ID and business both match the authenticated owner.
     */
    @Transactional(readOnly = true)
    public CustomerResponse get(UUID ownerId, UUID customerId) {
        UUID businessId = businessScopeService.requireBusiness(ownerId).getId();
        return toResponse(findCustomer(customerId, businessId));
    }

    /**
     * Updates editable customer data without allowing its identity or business to change.
     */
    @Transactional
    public CustomerResponse update(UUID ownerId, UUID customerId, UpdateCustomerRequest request) {
        UUID businessId = businessScopeService.requireBusiness(ownerId).getId();
        Customer customer = findCustomer(customerId, businessId);
        String name = validateName(request.name());
        String phone = phoneSecurity.normalize(request.phone());
        String lookupHash = phoneSecurity.lookupHash(phone);
        if (!lookupHash.equals(customer.getPhoneLookupHash())) {
            customerRepository.findByBusinessIdAndPhoneLookupHash(businessId, lookupHash)
                    .filter(existing -> !existing.getId().equals(customerId))
                    .ifPresent(existing -> { throw duplicatePhone(); });
            customer.setPhoneLookupHash(lookupHash);
            customer.setPhoneEncrypted(phoneSecurity.encrypt(phone));
        }
        customer.setName(name);
        customer.setUpdatedAt(clock.instant());
        return toResponse(customer);
    }

    private Customer findCustomer(UUID customerId, UUID businessId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer was not found."));
    }

    private String validateName(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isBlank() || name.length() > 120 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer name must contain 1 to 120 valid characters.");
        }
        return name;
    }

    private ApiException duplicatePhone() {
        return new ApiException(HttpStatus.CONFLICT, "A customer with this phone number already exists.");
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(),
                phoneSecurity.decrypt(customer.getPhoneEncrypted()), customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
