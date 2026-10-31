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

Tests use PostgreSQL Testcontainers and require Docker. The application connects to the
configured PostgreSQL database and applies `src/main/resources/db/migration` on startup.
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

## Production setup still required

- Configure a production SMTP provider and delivery monitoring.
- Configure and verify the Google OAuth client ID and allowed OAuth origins.
- Use HTTPS, secure cookies, managed secret storage, and production database credentials.
- Add shared/distributed rate limiting for registration and OTP endpoints before scaling;
  this implementation enforces OTP attempts and per-account resend cooldown only.
- Configure deployment, backups, monitoring, alerting, and operational incident procedures.
- Account linking, phone OTP, and authenticated Google-linking are intentionally not part
  of this phase.
