package review_engine.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import review_engine.project.service.auth.EmailService;

import java.util.UUID;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL-backed application and API tests covering migrations, security, and
 * the email registration, verification, login, and refresh-cookie flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("dev")
class ProjectApplicationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt.secret", () -> "test-only-jwt-secret-at-least-thirty-two-bytes");
        registry.add("app.security.otp.hmac-secret", () -> "test-only-otp-secret-at-least-thirty-two-bytes");
        registry.add("app.security.google.client-id", () -> "review-engine-test-client");
        registry.add("app.security.cookie.secure", () -> "false");
        registry.add("app.email.dev-return-otp", () -> "true");
        registry.add("app.customer.phone.encryption-key",
                () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.customer.phone.hmac-key",
                () -> java.util.Base64.getEncoder().encodeToString("integration-phone-hmac-key-material!".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
        registry.add("app.customer.phone.default-region", () -> "ZZ");
        registry.add("review-engine.feedback.token-secret",
                () -> "integration-feedback-token-secret-at-least-thirty-two-bytes");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private EmailService emailService;

    /**
     * Confirms Flyway created the PostgreSQL schema and unauthenticated access is denied.
     *
     * @throws Exception if the mock HTTP request fails
     */
    @Test
    void shouldRunFlywayMigrationsAndProtectCurrentUserEndpoint() throws Exception {
        Integer authTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'auth_identities'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, authTableCount);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Runs registration, OTP verification, login, refresh rotation, and reuse rejection.
     *
     * @throws Exception if an HTTP interaction fails
     */
    @Test
    void shouldRegisterVerifyLoginAndRotateRefreshToken() throws Exception {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());
        String email = "phase2-test@example.test";
        String registration = """
                {"name":"Test Owner","email":"%s","password":"Secure-Passphrase-984"}
                """.formatted(email);

        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailVerificationRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String otp = com.jayway.jsonpath.JsonPath.read(registerResponse, "$.data.developmentOtp");
        verify(emailService).sendVerificationCode(org.mockito.ArgumentMatchers.eq(email), anyString());

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secure-Passphrase-984"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(emptyOrNullString())))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();

        String loginJson = loginResult.getResponse().getContentAsString();
        String accessToken = com.jayway.jsonpath.JsonPath.read(loginJson, "$.data.accessToken");
        String userId = com.jayway.jsonpath.JsonPath.read(loginJson, "$.data.user.id");
        String businessRequest = """
                {"name":"Test Cafe","category":"Cafe","address":"10 Main Street",
                 "timezone":"America/New_York","googleReviewUrl":"https://g.page/r/example",
                 "ownerUserId":"%s"}
                """.formatted(UUID.randomUUID());
        var businessResult = mockMvc.perform(post("/api/business")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String businessId = com.jayway.jsonpath.JsonPath.read(
                businessResult.getResponse().getContentAsString(), "$.data.id");
        UUID persistedOwner = jdbcTemplate.queryForObject(
                "select owner_user_id from businesses where id = cast(? as uuid)", UUID.class, businessId);
        org.junit.jupiter.api.Assertions.assertEquals(UUID.fromString(userId), persistedOwner);
        mockMvc.perform(get("/api/business/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/business")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessRequest))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasBusiness").value(true));

        jakarta.servlet.http.Cookie oldRefresh = loginResult.getResponse().getCookie("refresh_token");
        jakarta.servlet.http.Cookie csrf = loginResult.getResponse().getCookie("XSRF-TOKEN");
        var refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldRefresh, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(emptyOrNullString())))
                .andReturn();

        jakarta.servlet.http.Cookie rotatedRefresh = refreshResult.getResponse().getCookie("refresh_token");
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldRefresh, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(rotatedRefresh, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Confirms business creation requires a bearer token and logout requires CSRF.
     *
     * @throws Exception if a mock HTTP request fails
     */
    @Test
    void shouldRequireAuthenticationAndCsrfForCookieOperations() throws Exception {
        mockMvc.perform(post("/api/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cafe","category":"Cafe","address":"Main Street",
                                 "timezone":"UTC","googleReviewUrl":null}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }

    /**
     * Exercises PostgreSQL uniqueness and foreign-key constraints across identity and business rows.
     */
    @Test
    void shouldEnforcePostgresIdentityAndBusinessConstraints() {
        String suffix = UUID.randomUUID().toString();
        UUID userId = insertUser("identity-" + suffix + "@example.test");
        UUID emailIdentityId = insertIdentity(userId, "EMAIL_PASSWORD",
                "identity-" + suffix + "@example.test", "bcrypt-hash");
        insertIdentity(userId, "GOOGLE", "google-sub-" + suffix, null);

        Integer identityCount = jdbcTemplate.queryForObject(
                "select count(*) from auth_identities where user_id = ?", Integer.class, userId);
        org.junit.jupiter.api.Assertions.assertEquals(2, identityCount);
        String googlePassword = jdbcTemplate.queryForObject(
                "select password_hash from auth_identities where provider = 'GOOGLE' and user_id = ?",
                String.class, userId);
        org.junit.jupiter.api.Assertions.assertNull(googlePassword);

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertIdentity(userId, "GOOGLE", "google-sub-" + suffix, null));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertIdentity(UUID.randomUUID(), "GOOGLE", "orphan-sub-" + suffix, null));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertUser("identity-" + suffix + "@example.test"));

        UUID businessId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into businesses(id, owner_user_id, name, category, address, timezone, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, businessId, userId, "Constraint Test", "Retail", "Address", "UTC", now, now);
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        insert into businesses(id, owner_user_id, name, category, address, timezone, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), userId, "Duplicate", "Retail", "Address", "UTC", now, now));
        org.junit.jupiter.api.Assertions.assertNotNull(emailIdentityId);
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success", Integer.class));
    }

    /**
     * Exercises customer management, transaction recording, database scoping, and the bearer filter chain.
     *
     * @throws Exception if a mock HTTP request fails
     */
    @Test
    void shouldCompleteOwnerScopedCustomerAndTransactionWorkflow() throws Exception {
        UUID ownerA = insertUser("owner-a-" + UUID.randomUUID() + "@example.test");
        UUID ownerB = insertUser("owner-b-" + UUID.randomUUID() + "@example.test");
        UUID businessA = insertBusiness(ownerA, "Owner A");
        UUID businessB = insertBusiness(ownerB, "Owner B");
        String tokenA = accessToken(ownerA);
        String tokenB = accessToken(ownerB);
        String phoneA = "+14155552671";
        String customerBody = """
                {"name":"  Alice A  ","phone":"%s","businessId":"%s","ownerUserId":"%s"}
                """.formatted(phoneA, businessB, ownerB);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(customerBody))
                .andExpect(status().isUnauthorized());

        var createA = mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(customerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Alice A"))
                .andExpect(jsonPath("$.data.phone").value(phoneA))
                .andExpect(jsonPath("$.data.phoneEncrypted").doesNotExist())
                .andExpect(jsonPath("$.data.phoneLookupHash").doesNotExist())
                .andReturn();
        String customerA = com.jayway.jsonpath.JsonPath.read(
                createA.getResponse().getContentAsString(), "$.data.id");
        String storedCiphertext = jdbcTemplate.queryForObject(
                "select phone_encrypted from customers where id = cast(? as uuid)", String.class, customerA);
        String storedHash = jdbcTemplate.queryForObject(
                "select phone_lookup_hash from customers where id = cast(? as uuid)", String.class, customerA);
        org.junit.jupiter.api.Assertions.assertTrue(storedCiphertext.startsWith("v1:"));
        org.junit.jupiter.api.Assertions.assertNotEquals(phoneA, storedCiphertext);
        org.junit.jupiter.api.Assertions.assertEquals(64, storedHash.length());
        org.junit.jupiter.api.Assertions.assertEquals(businessA, jdbcTemplate.queryForObject(
                "select business_id from customers where id = cast(? as uuid)", UUID.class, customerA));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(customerBody))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content(customerBody))
                .andExpect(status().isCreated());

        String customerBody2 = """
                {"name":"Alice Second","phone":"+14155552672"}
                """;
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(customerBody2))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("name", "Alice").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(get("/api/customers/" + customerA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/customers/" + customerA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijack\",\"phone\":\"+14155552673\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/customers/" + customerA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(phoneA));
        mockMvc.perform(put("/api/customers/" + customerA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice Updated\",\"phone\":\"+14155552673\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alice Updated"))
                .andExpect(jsonPath("$.data.phone").value("+14155552673"));
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"phone\":\"+14155552674\"}"))
                .andExpect(status().isBadRequest());

        String transactionBody = """
                {"customerId":"%s","referenceCode":"ORD-2026-001","status":"PENDING","businessId":"%s"}
                """.formatted(customerA, businessB);
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerA + "\"}"))
                .andExpect(status().isUnauthorized());
        var createTransaction = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(transactionBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists())
                .andReturn();
        String transactionId = com.jayway.jsonpath.JsonPath.read(
                createTransaction.getResponse().getContentAsString(), "$.data.id");
        org.junit.jupiter.api.Assertions.assertEquals(businessA, jdbcTemplate.queryForObject(
                "select business_id from transactions where id = cast(? as uuid)", UUID.class, transactionId));
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"%s\"}".formatted(customerA)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"%s\",\"referenceCode\":\"ORD-2026-001\"}".formatted(customerA)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"%s\"}".formatted(customerA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/transactions/" + transactionId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/transactions/" + transactionId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(customerA));
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("customerId", customerA).param("page", "0").param("size", "1")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("from", OffsetDateTime.now().minusDays(1).toString())
                        .param("to", OffsetDateTime.now().plusDays(1).toString())
                        .param("referenceCode", "ORD-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        String future = OffsetDateTime.now().plusMinutes(6).toString();
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"%s\",\"completedAt\":\"%s\"}".formatted(customerA, future)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("from", "2026-11-02T00:00:00Z").param("to", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA).param("status", "PENDING"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Confirms PostgreSQL enforces tenant-consistent relationships and nullable reference uniqueness.
     */
    @Test
    void shouldEnforceCustomerTransactionConstraintsAndIndexesInPostgres() {
        UUID ownerA = insertUser("constraints-a-" + UUID.randomUUID() + "@example.test");
        UUID ownerB = insertUser("constraints-b-" + UUID.randomUUID() + "@example.test");
        UUID businessA = insertBusiness(ownerA, "Constraints A");
        UUID businessB = insertBusiness(ownerB, "Constraints B");
        UUID customerA = insertCustomer(businessA, "a".repeat(64));
        UUID customerB = insertCustomer(businessB, "a".repeat(64));
        Timestamp now = Timestamp.from(Instant.now());

        org.junit.jupiter.api.Assertions.assertNotEquals(customerA, customerB);
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertCustomer(businessA, "a".repeat(64)));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertCustomer(UUID.randomUUID(), UUID.randomUUID().toString().replace("-", "")));
        insertTransaction(businessA, customerA, null, now);
        insertTransaction(businessA, customerA, null, now);
        insertTransaction(businessA, customerA, "REF-DB-1", now);
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertTransaction(businessA, customerA, "REF-DB-1", now));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertTransaction(businessB, customerA, null, now));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertTransaction(UUID.randomUUID(), customerA, null, now));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> insertTransaction(businessA, customerA, "BAD-STATUS", now, "PENDING"));

        Integer indexCount = jdbcTemplate.queryForObject("""
                select count(*) from pg_indexes
                where schemaname = current_schema()
                  and indexname in ('ix_customers_business_created', 'ix_customers_business_name',
                                    'ix_transactions_business_completed', 'ix_transactions_customer_completed',
                                    'uk_transactions_business_reference')
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(5, indexCount);
    }

    /**
     * Confirms OTP consumption and hashed refresh-token revocation persist in PostgreSQL.
     */
    @Test
    void shouldPersistOtpConsumptionAndRefreshTokenRevocation() {
        String suffix = UUID.randomUUID().toString();
        UUID userId = insertUser("otp-" + suffix + "@example.test");
        UUID identityId = insertIdentity(userId, "EMAIL_PASSWORD", "otp-" + suffix + "@example.test", "bcrypt-hash");
        UUID otpId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into email_verification_otps
                (id, user_id, auth_identity_id, otp_hash, expires_at, attempts, created_at)
                values (?, ?, ?, ?, ?, 0, ?)
                """, otpId, userId, identityId, "a".repeat(64), Timestamp.from(Instant.now().plusSeconds(300)), now);
        jdbcTemplate.update("update email_verification_otps set consumed_at = ? where id = ?", now, otpId);
        org.junit.jupiter.api.Assertions.assertNotNull(jdbcTemplate.queryForObject(
                "select consumed_at from email_verification_otps where id = ?", Timestamp.class, otpId));

        UUID refreshId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into refresh_tokens(id, user_id, token_hash, expires_at, created_at)
                values (?, ?, ?, ?, ?)
                """, refreshId, userId, "b".repeat(64), Timestamp.from(Instant.now().plusSeconds(3600)), now);
        jdbcTemplate.update("update refresh_tokens set revoked_at = ? where id = ?", now, refreshId);
        org.junit.jupiter.api.Assertions.assertNotNull(jdbcTemplate.queryForObject(
                "select revoked_at from refresh_tokens where id = ?", Timestamp.class, refreshId));
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into users(id, name, email, account_status, created_at, updated_at)
                values (?, ?, ?, 'ACTIVE', ?, ?)
                """, id, "Test User", email, now, now);
        return id;
    }

    private UUID insertBusiness(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into businesses(id, owner_user_id, name, category, address, timezone, created_at, updated_at)
                values (?, ?, ?, 'Retail', 'Test address', 'UTC', ?, ?)
                """, id, ownerId, name, now, now);
        return id;
    }

    private UUID insertCustomer(UUID businessId, String hash) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into customers(id, business_id, name, phone_encrypted, phone_lookup_hash, created_at, updated_at)
                values (?, ?, 'Constraint Customer', 'v1:test:test', ?, ?, ?)
                """, id, businessId, hash, now, now);
        return id;
    }

    private void insertTransaction(UUID businessId, UUID customerId, String referenceCode, Timestamp now) {
        insertTransaction(businessId, customerId, referenceCode, now, "COMPLETED");
    }

    private void insertTransaction(UUID businessId, UUID customerId, String referenceCode, Timestamp now,
                                  String status) {
        jdbcTemplate.update("""
                insert into transactions(id, business_id, customer_id, reference_code, status,
                                         completed_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), businessId, customerId, referenceCode, status, now, now, now);
    }

    private String accessToken(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("review-engine")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("token_type", "access")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private UUID insertIdentity(UUID userId, String provider, String subject, String passwordHash) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into auth_identities
                (id, user_id, provider, provider_subject, password_hash, verified_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, userId, provider, subject, passwordHash, now, now, now);
        return id;
    }

    /**
     * Confirms input constraints reject invalid email before persistence.
     *
     * @throws Exception if the mock HTTP request fails
     */
    @Test
    void shouldRejectInvalidRegistrationRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
