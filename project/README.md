# Review Engine Backend

Spring Boot 4.1.2 backend on Java 21. The service uses Spring Security, Spring Data JPA,
PostgreSQL, and Flyway. Schema changes are applied by Flyway; Hibernate validates the
resulting schema at startup.

## Local configuration

Set these variables in the process environment before starting the application. Do not
commit real credentials or secret values.

| Variable | Required | Purpose |
| --- | --- | --- |
| `DB_URL` | Yes | PostgreSQL JDBC URL, for example a Neon JDBC endpoint |
| `DB_USERNAME` | Yes | Database username |
| `DB_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | At least 32 bytes of private signing key material |
| `OTP_HMAC_SECRET` | Yes | At least 32 bytes of private OTP HMAC key material |
| `PHONE_ENCRYPTION_KEY` | Yes | Base64-encoded 32-byte AES-256 key for customer phones |
| `PHONE_HMAC_KEY` | Yes | Base64-encoded independent key of at least 32 bytes for phone lookup |
| `PHONE_DEFAULT_REGION` | No | ISO region for national-format phone input; defaults to `ZZ` (international numbers only) |
| `GOOGLE_CLIENT_ID` | For Google sign-in | OAuth client ID accepted as the token audience |
| `JWT_ISSUER` | No | JWT issuer; defaults to `review-engine` |
| `JWT_ACCESS_TOKEN_MINUTES` | No | Access-token lifetime; defaults to 15 |
| `REFRESH_TOKEN_DAYS` | No | Refresh-token lifetime; defaults to 30 |
| `OTP_EXPIRY_MINUTES` | No | Verification-code lifetime; defaults to 5 |
| `OTP_RESEND_COOLDOWN_SECONDS` | No | Per-identity resend cooldown; defaults to 60 |
| `OTP_MAX_ATTEMPTS` | No | Maximum guesses per code; defaults to 5 |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated exact browser origins; defaults to `http://localhost:3000` |
| `COOKIE_SECURE` | No | Secure-cookie flag; defaults to `true` |
| `COOKIE_SAME_SITE` | No | Cookie SameSite policy; defaults to `Strict` |
| `EMAIL_FROM` | No | Sender address; defaults to `no-reply@localhost` |
| `DEV_RETURN_OTP` | No | When `true` with the `dev` profile, returns OTPs for local-only testing |

Spring Boot Mail also reads standard `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` and `SPRING_MAIL_PROPERTIES_*` settings.
Without an SMTP host configured, registration reports that email delivery is unavailable.
OTP codes are never logged. The development response option is disabled by default and
must not be enabled in production. In the `dev` profile with this switch enabled, the
development mail adapter deliberately does not contact an external provider; registration
and eligible resend responses contain the code for local testing.

For local HTTP testing, set `COOKIE_SECURE=false`; use `true` and HTTPS outside local
development. Keep the allowed-origin list explicit. No Google or email credentials are
provided by this repository.

## Start and verify

From the `project` directory:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Container-backed PostgreSQL integration tests use Testcontainers and require Docker; the
integration test class is disabled when Docker is unavailable. The application connects
to the configured PostgreSQL database and applies `src/main/resources/db/migration` on startup.
The first migration is `V1__create_auth_and_business_schema.sql`.

## Authentication and onboarding

Registering creates a pending account and an `EMAIL_PASSWORD` identity only. It does not
create a business or issue a token. Email must be verified before password login.
Google authentication verifies the signed ID token, issuer, expiry, audience, and verified
email using Google's token verifier. The stable Google `sub` claim identifies the
provider identity. A Google email matching an existing account is not silently linked;
the existing account must first be authenticated. An authenticated linking flow is not
implemented in this phase.

Refresh tokens are random opaque values stored only as SHA-256 digests. The refresh token
is sent as an HttpOnly cookie scoped to `/api/auth`; the access JWT is returned in JSON
and is intended for in-memory browser use. Refresh rotates tokens; replaying a previously
rotated token revokes active refresh tokens for that account. The `XSRF-TOKEN` cookie and
matching `X-XSRF-TOKEN` header are required for refresh and logout. Other unsafe API
operations authenticate using an explicit bearer access token and do not rely on ambient
cookies. CORS allows credentials only for the configured explicit origins.

Every active account may create at most one business. Ownership is derived from the
validated JWT principal; owner and business IDs are not accepted as authoritative
client-supplied values. `GET /api/auth/me` reports `hasBusiness` from database state.

## API contracts

All responses use an envelope:

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": {},
  "timestamp": "2026-01-01T12:00:00Z"
}
```

Errors use the same structure with `success: false`, a safe message, and `data: null`.

| Method and path | Request body | Authentication | Result |
| --- | --- | --- | --- |
| `POST /api/auth/register` | `{"name":"Name","email":"person@example.com","password":"long passphrase"}` | Public | `201` response requiring email verification; no tokens |
| `POST /api/auth/verify-email` | `{"email":"person@example.com","otp":"123456"}` | Public | Activates the account; a code expires after five minutes by default |
| `POST /api/auth/resend-otp` | `{"email":"person@example.com"}` | Public | Generic response; per-identity cooldown applies |
| `POST /api/auth/login` | `{"email":"person@example.com","password":"long passphrase"}` | Public | Access JWT in response; refresh and CSRF cookies |
| `POST /api/auth/google` | `{"idToken":"<Google ID token>"}` | Public | Same application token format as password login |
| `POST /api/auth/refresh` | No body | Refresh and CSRF cookies plus `X-XSRF-TOKEN` header | Rotates refresh cookie and returns a new access JWT |
| `POST /api/auth/logout` | No body | Refresh and CSRF cookies plus `X-XSRF-TOKEN` header | Revokes current refresh token and clears cookies |
| `GET /api/auth/me` | — | Bearer access JWT | Public user fields and `hasBusiness` onboarding state |
| `POST /api/business` | `{"name":"Cafe","category":"Restaurant","address":"...","timezone":"America/New_York","googleReviewUrl":"https://..."}` | Bearer access JWT | Creates the one profile owned by the caller |
| `GET /api/business/me` | — | Bearer access JWT | Returns only the caller's business; `404` if onboarding is incomplete |
| `PUT /api/business/me` | Same profile fields as create | Bearer access JWT | Updates permitted business fields |
| `PATCH /api/business/me/google-review-url` | `{"googleReviewUrl":"https://..."}` | Bearer access JWT | Updates only the review URL |

Business timezones must be valid IANA zone IDs. Review URLs must be absolute HTTPS URLs
with a host and without user-info. The backend does not claim that a review URL is
verified by Google.

## Customer and transaction management

These endpoints require an authenticated bearer access JWT and an existing business
profile. The server derives the business from the JWT subject; request bodies never
establish owner or business identity. Access to an unknown or another owner's resource
returns `404`.

All responses use the standard envelope shown above. Common errors are `400` for invalid
input, `401` for missing/invalid authentication, `404` for inaccessible resources or
missing business onboarding, `409` for duplicate phones/references, and `500` for
unexpected server errors. Error bodies do not contain SQL details or submitted phone
values.

### Customers

| Method and path | Request body / query | Success | Behavior |
| --- | --- | --- | --- |
| `POST /api/customers` | `{"name":"Sample Customer","phone":"+14155550100"}` | `201` with `CustomerResponse` | Trims the name; validates and normalizes phone to E.164; phone is unique per business |
| `GET /api/customers` | `page` (default `0`), `size` (default `20`, maximum `100`), `sort` (default `createdAt,desc`), optional `name` | `200` with `PageResponse<CustomerSummaryResponse>` | Database pagination and optional case-insensitive name search; allowed sort fields: `name`, `createdAt`, `updatedAt` |
| `GET /api/customers/{customerId}` | UUID path parameter | `200` with `CustomerResponse` | Resolves the ID only inside the owner's business |
| `PUT /api/customers/{customerId}` | `{"name":"Updated Name","phone":"+14155550100"}` | `200` with `CustomerResponse` | Replaces name and phone; ID, business, and creation time cannot change |

Customer names are required, trimmed, nonblank, at most 120 characters, and cannot
contain control characters. Phone numbers are required and validated by Google's
libphonenumber. International `+` numbers are accepted by default; set
`PHONE_DEFAULT_REGION` to an ISO region to also accept national-format numbers.
Create, detail, and update responses contain the normalized phone only inside the
authenticated owner's scope. Collection summaries omit the phone, avoiding unnecessary
decryption; encrypted values and lookup hashes are never returned. Customer records
cannot be deleted in this phase.

Customer pages include `content`, zero-based `pageNumber`, `pageSize`, `totalElements`,
`totalPages`, `hasNext`, and `hasPrevious`. The maximum page size is 100. Unsupported sort
fields/directions and invalid page bounds return `400`.

### Transactions

| Method and path | Request body / query | Success | Behavior |
| --- | --- | --- | --- |
| `POST /api/transactions` | `{"customerId":"<customer-uuid>","referenceCode":"ORDER-001","completedAt":"2026-11-01T10:30:00Z"}` | `201` with `TransactionResponse` | Records a transaction for a customer in the owner's business; reference and timestamp are optional |
| `GET /api/transactions` | `page` (default `0`), `size` (default `20`, maximum `100`), `sort` (default `completedAt,desc`), optional `customerId`, `from`, `to`, `status`, `referenceCode` | `200` with `PageResponse<TransactionResponse>` | Database pagination; date bounds are inclusive and accept ISO-8601 timestamps with an offset |
| `GET /api/transactions/{transactionId}` | UUID path parameter | `200` with `TransactionResponse` | Resolves the ID only inside the owner's business |

The create request does not expose a status field. If one is supplied as an unknown
request property, it is ignored; the backend always persists `COMPLETED`. The customer
must already belong to the authenticated owner's business. `referenceCode` is trimmed,
must be nonblank if present, and is limited to 100 characters; a non-null reference is
unique within its business. Multiple null references are allowed.

If `completedAt` is omitted, the current server time is used. Supplied timestamps must
include an offset and are stored as UTC instants in PostgreSQL `TIMESTAMPTZ`. Values more
than five minutes ahead of the server clock are rejected; there is no short historical
cutoff. List filters accept only `COMPLETED` for `status`; `from` cannot be after `to`.
Allowed sort fields are `completedAt`, `createdAt`, and `referenceCode`. Transaction
update and deletion endpoints are intentionally not provided.

### Phone protection and database migration

`PHONE_ENCRYPTION_KEY` must be a Base64 encoding of exactly 32 random bytes. It is used
only for AES-256-GCM encryption. `PHONE_HMAC_KEY` must be a separate Base64-encoded key
of at least 32 random bytes and is used only for HMAC-SHA-256 duplicate lookup. Keep both
in a secret manager or process environment; never commit them or reuse the JWT/OTP keys.
The versioned `v1:<nonce>:<ciphertext>` representation stores URL-safe Base64 values,
uses a fresh 96-bit nonce for each encryption, and authenticates the version as GCM
associated data. The version marker allows later key-versioned rotation; this release
loads the active `v1` key and does not include an automated re-encryption/rotation tool.

Migration `V2__create_customers_and_transactions.sql` adds the customer and transaction
tables without changing V1. It enforces unique customer phone hashes per business,
unique non-null transaction references per business, completed-only transaction status,
foreign keys, and a composite customer/business foreign key that prevents cross-business
transactions. Hibernate remains in `validate` mode.

Run the backend and tests from the `project` directory:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

PostgreSQL repository and API integration tests use Testcontainers. Docker is needed to
execute those tests; in environments without Docker, the test configuration disables
the container-backed integration test class. Unit tests run without Docker.

## Production setup still required

- Configure a production SMTP provider and delivery monitoring.
- Configure and verify the Google OAuth client ID and allowed OAuth origins.
- Use HTTPS, secure cookies, managed secret storage, and production database credentials.
- Add shared/distributed rate limiting for registration and OTP endpoints before scaling;
  this implementation enforces OTP attempts and per-account resend cooldown only.
- Configure deployment, backups, monitoring, alerting, and operational incident procedures.
- Provision and rotate the dedicated phone encryption and HMAC secrets; implement a
  reviewed key-rotation procedure before changing the active encryption key.
- Account linking, phone OTP, and authenticated Google-linking are intentionally not part
  of this phase.
