package review_engine.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import review_engine.project.dto.request.CreateCustomerRequest;
import review_engine.project.dto.request.CreateTransactionRequest;
import review_engine.project.dto.request.UpdateCustomerRequest;
import review_engine.project.entity.Business;
import review_engine.project.entity.Customer;
import review_engine.project.entity.TransactionEntity;
import review_engine.project.enums.TransactionStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.CustomerRepository;
import review_engine.project.repository.TransactionRepository;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.CustomerService;
import review_engine.project.service.PhoneNumberSecurityService;
import review_engine.project.service.TransactionService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for customer and completed-transaction business rules and ownership scoping.
 */
class CustomerTransactionServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private final UUID ownerId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();
    private Business business;
    private BusinessScopeService businessScope;
    private CustomerRepository customerRepository;
    private TransactionRepository transactionRepository;
    private PhoneNumberSecurityService phoneSecurity;
    private CustomerService customerService;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(businessId);
        businessScope = mock(BusinessScopeService.class);
        customerRepository = mock(CustomerRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        phoneSecurity = mock(PhoneNumberSecurityService.class);
        when(businessScope.requireBusiness(ownerId)).thenReturn(business);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        customerService = new CustomerService(customerRepository, businessScope, phoneSecurity, clock);
        transactionService = new TransactionService(transactionRepository, customerRepository, businessScope, clock);
    }

    @Test
    void createsCustomerWithTrimmedNameAndEncryptedNormalizedPhone() {
        when(phoneSecurity.normalize("+1 415 555 2671")).thenReturn("+14155552671");
        when(phoneSecurity.lookupHash("+14155552671")).thenReturn("hash");
        when(phoneSecurity.encrypt("+14155552671")).thenReturn("v1:nonce:cipher");
        when(phoneSecurity.decrypt("v1:nonce:cipher")).thenReturn("+14155552671");
        when(customerRepository.findByBusinessIdAndPhoneLookupHash(businessId, "hash"))
                .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(UUID.randomUUID());
            return customer;
        });

        var result = customerService.create(ownerId, new CreateCustomerRequest("  Alex Owner  ", "+1 415 555 2671"));

        assertEquals("Alex Owner", result.name());
        assertEquals("+14155552671", result.phone());
        verify(customerRepository).save(any(Customer.class));
        verify(phoneSecurity).encrypt("+14155552671");
    }

    @Test
    void rejectsDuplicatePhoneWithinBusinessBeforeEncryption() {
        when(phoneSecurity.normalize("+14155552671")).thenReturn("+14155552671");
        when(phoneSecurity.lookupHash("+14155552671")).thenReturn("hash");
        when(customerRepository.findByBusinessIdAndPhoneLookupHash(businessId, "hash"))
                .thenReturn(Optional.of(new Customer()));

        ApiException error = assertThrows(ApiException.class,
                () -> customerService.create(ownerId, new CreateCustomerRequest("Alex", "+14155552671")));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(phoneSecurity, never()).encrypt(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void rejectsControlCharactersInCustomerNames() {
        ApiException error = assertThrows(ApiException.class,
                () -> customerService.create(ownerId, new CreateCustomerRequest("Bad\nName", "+14155552671")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(phoneSecurity, never()).normalize(any());
    }

    @Test
    void allowsPhoneHashAlreadyUsedByAnotherBusiness() {
        UUID secondOwner = UUID.randomUUID();
        UUID secondBusinessId = UUID.randomUUID();
        Business secondBusiness = new Business();
        secondBusiness.setId(secondBusinessId);
        when(businessScope.requireBusiness(secondOwner)).thenReturn(secondBusiness);
        when(phoneSecurity.normalize("+14155552671")).thenReturn("+14155552671");
        when(phoneSecurity.lookupHash("+14155552671")).thenReturn("same-hash");
        when(phoneSecurity.encrypt("+14155552671")).thenReturn("v1:nonce:cipher");
        when(phoneSecurity.decrypt("v1:nonce:cipher")).thenReturn("+14155552671");
        when(customerRepository.findByBusinessIdAndPhoneLookupHash(secondBusinessId, "same-hash"))
                .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        customerService.create(secondOwner, new CreateCustomerRequest("Alex", "+14155552671"));

        verify(customerRepository).findByBusinessIdAndPhoneLookupHash(secondBusinessId, "same-hash");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void rejectsCrossBusinessCustomerReadAndUpdate() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, () -> customerService.get(ownerId, customerId)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, () -> customerService.update(
                        ownerId, customerId, new UpdateCustomerRequest("Alex", "+14155552671"))).getStatus());
        verify(customerRepository, never()).findById(customerId);
    }

    @Test
    void updatesPhoneOnlyAfterBusinessScopedDuplicateCheck() {
        UUID customerId = UUID.randomUUID();
        Customer existing = customer(customerId, "+14155552671", "old-hash");
        when(customerRepository.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(existing));
        when(phoneSecurity.normalize("+14155552672")).thenReturn("+14155552672");
        when(phoneSecurity.lookupHash("+14155552672")).thenReturn("new-hash");
        when(customerRepository.findByBusinessIdAndPhoneLookupHash(businessId, "new-hash"))
                .thenReturn(Optional.empty());
        when(phoneSecurity.encrypt("+14155552672")).thenReturn("v1:new-nonce:cipher");
        when(phoneSecurity.decrypt("v1:new-nonce:cipher")).thenReturn("+14155552672");

        var result = customerService.update(ownerId, customerId,
                new UpdateCustomerRequest("  New Name ", "+14155552672"));

        assertEquals("New Name", result.name());
        assertEquals("+14155552672", result.phone());
        assertEquals("new-hash", existing.getPhoneLookupHash());
        verify(phoneSecurity).encrypt("+14155552672");
    }

    @Test
    void rejectsDuplicatePhoneOnUpdateWithoutChangingCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer existing = customer(customerId, "+14155552671", "old-hash");
        Customer duplicate = customer(UUID.randomUUID(), "+14155552672", "new-hash");
        when(customerRepository.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(existing));
        when(phoneSecurity.normalize("+14155552672")).thenReturn("+14155552672");
        when(phoneSecurity.lookupHash("+14155552672")).thenReturn("new-hash");
        when(customerRepository.findByBusinessIdAndPhoneLookupHash(businessId, "new-hash"))
                .thenReturn(Optional.of(duplicate));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> customerService.update(ownerId, customerId,
                        new UpdateCustomerRequest("New Name", "+14155552672"))).getStatus());
        assertEquals("old-hash", existing.getPhoneLookupHash());
        verify(phoneSecurity, never()).encrypt(any());
    }

    @Test
    void recordsCompletedTransactionWithServerDefaultTime() {
        Customer customer = customer(UUID.randomUUID(), null, null);
        when(customerRepository.findByIdAndBusinessId(customer.getId(), businessId))
                .thenReturn(Optional.of(customer));
        when(transactionRepository.existsByBusinessIdAndReferenceCode(businessId, "REF-1")).thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });

        var result = transactionService.create(ownerId,
                new CreateTransactionRequest(customer.getId(), " REF-1 ", null));

        assertEquals(TransactionStatus.COMPLETED, result.status());
        assertEquals(NOW, result.completedAt());
        assertEquals("REF-1", result.referenceCode());
        verify(transactionRepository).save(any(TransactionEntity.class));
    }

    @Test
    void allowsNullReferenceAndRejectsDuplicateReference() {
        Customer customer = customer(UUID.randomUUID(), null, null);
        when(customerRepository.findByIdAndBusinessId(customer.getId(), businessId))
                .thenReturn(Optional.of(customer));
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(ownerId, new CreateTransactionRequest(customer.getId(), null, null));
        verify(transactionRepository, never()).existsByBusinessIdAndReferenceCode(businessId, null);

        when(transactionRepository.existsByBusinessIdAndReferenceCode(businessId, "REF-2")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> transactionService.create(ownerId,
                        new CreateTransactionRequest(customer.getId(), "REF-2", null))).getStatus());
    }

    @Test
    void rejectsMissingOrCrossBusinessCustomer() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> transactionService.create(ownerId, new CreateTransactionRequest(customerId, null, null)))
                .getStatus());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsCompletionMoreThanFiveMinutesAheadAndInvalidDateRanges() {
        Customer customer = customer(UUID.randomUUID(), null, null);
        when(customerRepository.findByIdAndBusinessId(customer.getId(), businessId)).thenReturn(Optional.of(customer));
        Instant tooFarAhead = NOW.plusSeconds(301);

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> transactionService.create(ownerId, new CreateTransactionRequest(customer.getId(), null,
                        tooFarAhead.atOffset(ZoneOffset.UTC)))).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> transactionService.validateDateRange(NOW.plusSeconds(1), NOW)).getStatus());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void returnsNotFoundForCrossBusinessTransaction() {
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findByIdAndBusinessId(transactionId, businessId)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> transactionService.get(ownerId, transactionId)).getStatus());
        verify(transactionRepository, never()).findById(transactionId);
    }

    @Test
    void listsCustomersByBusinessUsingDatabasePage() {
        Customer customer = customer(UUID.randomUUID(), "v1:encrypted", "hash");
        when(customerRepository.findAllByBusinessId(businessId, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(customer)));

        var page = customerService.list(ownerId, null, PageRequest.of(0, 10));

        assertEquals(1, page.content().size());
        assertEquals("Customer", page.content().getFirst().name());
        verify(phoneSecurity, never()).decrypt(any());
        verify(customerRepository).findAllByBusinessId(businessId, PageRequest.of(0, 10));
    }

    private Customer customer(UUID id, String encrypted, String hash) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setBusiness(business);
        customer.setName("Customer");
        customer.setPhoneEncrypted(encrypted);
        customer.setPhoneLookupHash(hash);
        return customer;
    }
}
