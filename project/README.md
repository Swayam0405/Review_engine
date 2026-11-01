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
| `FEEDBACK_TOKEN_SECRET` | Yes | At least 32 random bytes; keep stable because changing it invalidates issued feedback links |
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
| `FEEDBACK_TOKEN_EXPIRY` | No | Feedback-link lifetime; defaults to `14d` |
| `FEEDBACK_PUBLIC_BASE_URL` | No | Frontend feedback URL prefix; defaults to `http://localhost:3000/feedback`; HTTPS is required outside localhost |
| `FEEDBACK_RATE_LIMIT_PER_MINUTE` | No | Per-IP public feedback request limit; defaults to `30` per application instance |
| `FEEDBACK_EXPIRY_POLL_INTERVAL_MS` | No | Expiration-job interval; defaults to `60000` |
| `N8N_ENABLED` | No | Enables asynchronous delivery scheduling; defaults to `false` |
| `N8N_BASE_URL` | When n8n is enabled | n8n base URL; HTTPS is required outside localhost |
| `N8N_API_KEY` | When n8n is enabled | Credential sent to the configured n8n schedule webhook |
| `N8N_FEEDBACK_SCHEDULE_WEBHOOK` | When n8n is enabled | Relative webhook path or absolute HTTPS webhook URL |
| `N8N_INTERNAL_API_KEY` | When n8n is enabled | At least 32 bytes; authenticates n8n delivery-detail requests |
| `N8N_CALLBACK_SECRET` | When n8n is enabled | At least 32 bytes; authenticates delivery callbacks |
| `N8N_POLL_INTERVAL_MS` | No | Outbox poll interval; defaults to `5000` and must be at least `1000` |
| `AI_ENABLED` | No | Enables AI generation; defaults to `false` |
| `AI_PROVIDER` | No | Provider adapter; currently `openai-compatible` |
| `AI_API_KEY` | When AI is enabled | Provider credential; keep it in a secret manager |
| `AI_MODEL` | When AI is enabled | Provider model; defaults to `gpt-4o-mini` |
| `AI_BASE_URL` | No | OpenAI-compatible API base URL; defaults to `https://api.openai.com/v1`; HTTPS required except localhost |
| `AI_TIMEOUT` | No | Provider request timeout; defaults to `20s`, maximum `2m` |
| `AI_MAX_RETRIES` | No | Bounded retry count; defaults to `2`, maximum `5` |
| `AI_MAX_PROMPT_CHARACTERS` | No | Maximum serialized prompt/context size; defaults to `20000` |
| `AI_MAX_FOLLOW_UP_QUESTIONS` | No | Maximum optional AI follow-ups per feedback conversation; defaults to `3` |
| `AI_MAX_ANALYSIS_BATCH` | No | Maximum analyzed-feedback samples included in summary context; defaults to `20` |
| `AI_RATE_LIMIT_PER_MINUTE` | No | Per-IP limit for summary generation and review-draft generation per application instance; defaults to `10` |
| `AI_WORKER_INTERVAL` | No | Feedback-analysis outbox poll interval; defaults to `5s` |
| `DASHBOARD_DEFAULT_PERIOD_DAYS` | No | Dashboard and summary default period; defaults to `30` |
| `DASHBOARD_MAX_DATE_RANGE_DAYS` | No | Maximum inclusive period width accepted by analytics; defaults to `366` |
| `DASHBOARD_RECURRING_ISSUE_MIN_FEEDBACK_COUNT` | No | Minimum distinct analyzed feedback records before an issue is called recurring; defaults to `2` |
| `DASHBOARD_DEFAULT_RESULT_LIMIT` | No | Default topic/aspect/issue/highlight result limit; defaults to `20` |

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

Business timezones must be valid IANA zone IDs. Google review URLs must use HTTPS and a
supported Google review destination; the backend validates the host/path but does not
verify that the configured URL belongs to the business through Google's APIs.

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

## Feedback collection and delivery

Feedback-question and feedback-request management endpoints require the authenticated
owner's bearer JWT and derive the business from that identity. Requests can only be
created for completed transactions in that business; transaction IDs and question IDs
are always checked against that business. A question's text and answer type cannot be
changed after it has answers; deactivate it and create a replacement to preserve the
meaning of historical feedback.

| Method and path | Request body / query | Success | Behavior |
| --- | --- | --- | --- |
| `POST /api/feedback-questions` | `{"questionText":"How was your visit?","questionType":"TEXT","required":false,"displayOrder":0,"active":true}` | `201` | Creates a business-owned TEXT, RATING, or YES_NO question |
| `GET /api/feedback-questions` | Optional `active`, `page` (default `0`), `size` (default `50`, max `100`) | `200` | Lists only the owner's questions |
| `GET /api/feedback-questions/{questionId}` | — | `200` | Reads only a question in the owner's business |
| `PUT /api/feedback-questions/{questionId}` | Same fields as create | `200` | Updates question configuration subject to historical-answer protection |
| `PATCH /api/feedback-questions/{questionId}/status` | `{"active":false}` | `200` | Activates or deactivates the question |
| `POST /api/feedback-requests` | `{"transactionId":"<transaction-uuid>","scheduledAt":"2026-11-01T10:30:00Z"}` | `201` | Creates a one-per-transaction invitation and durable schedule event; schedule time is optional |
| `GET /api/feedback-requests` | Optional `status`, `page` (default `0`), `size` (default `20`, max `100`) | `200` | Lists requests only in the owner's business |
| `GET /api/feedback-requests/{requestId}` | — | `200` | Reads an owner's request without exposing phone or token hash |
| `PATCH /api/feedback-requests/{requestId}/cancel` | — | `200` | Cancels an incomplete request |
| `POST /api/feedback-requests/{requestId}/retry` | — | `200` | Queues a new schedule event for an unexpired failed request |
| `GET /api/public/feedback/{token}` | — | `200` | Validates an opaque, expiring link and returns active questions |
| `POST /api/public/feedback/{token}/conversation` | — | `200` | Starts or resumes the deterministic feedback conversation |
| `GET /api/public/feedback/{token}/conversation` | — | `200` | Reads conversation messages and current active questions |
| `POST /api/public/feedback/{token}/conversation/messages` | `{"content":"...","rating":5}` (`rating` optional) | `201` | Adds a validated customer message; AI follow-up is optional |
| `POST /api/public/feedback/{token}/submit` | `{"rating":5,"comment":"Great service","answers":[{"questionId":"<uuid>","answerText":"..." }]}` | `200` | Atomically stores one final rating, typed answers, and conversation completion |

Public feedback routes are limited per source IP and application instance. Deployments
with multiple instances should enforce an additional shared/distributed edge limit.
Links are derived using HMAC-SHA-256 from the request UUID and `FEEDBACK_TOKEN_SECRET`;
only a SHA-256 token digest is stored. Preserve that secret across deployments and
backups. Rotating it invalidates outstanding links because no raw token is stored.
Feedback requests expire after the configured lifetime; the scheduled job persists the
EXPIRED status. Completed feedback cannot be submitted again.

### Phase 5 AI analysis and review drafts

AI is disabled unless `AI_ENABLED=true`. When enabled, configure `AI_API_KEY` and
`AI_MODEL`; requests use the configured OpenAI-compatible endpoint. Provider calls have
bounded timeouts/retries and structured outputs are validated before persistence. Avoid
sending sensitive customer information to a provider unless permitted by your privacy
policy and provider agreement. AI-generated customer follow-ups are optional; if AI is
disabled or unavailable, the existing structured feedback conversation remains usable.

Finalized feedback is queued for asynchronous analysis in the durable outbox. The worker
stores validated analysis results; owner-scoped status and retry operations are available
below. Insights use deterministic database aggregations for metrics and use AI only for
evidence-grounded period summaries, which are cached for the exact requested period.
Date parameters accept ISO-8601 instants. By default, the service reports the preceding
30 days; a requested period must be positive and no longer than 366 days. AI summaries
require at least three completed feedback analyses for the period.

| Method and path | Request body / query | Authentication | Result |
| --- | --- | --- | --- |
| `GET /api/feedback/{feedbackId}/analysis` | — | Owner bearer JWT | Analysis status and persisted result for feedback in the owner's business |
| `POST /api/feedback/{feedbackId}/analysis/retry` | — | Owner bearer JWT | Queues an eligible analysis retry |
| `GET /api/insights/overview` | Optional `from`, `to` | Owner bearer JWT | Deterministic feedback overview |
| `GET /api/insights/topics` | Optional `from`, `to` | Owner bearer JWT | Topic metrics |
| `GET /api/insights/issues` | Optional `from`, `to` | Owner bearer JWT | Issue metrics |
| `GET /api/insights/trends` | Optional `from`, `to` | Owner bearer JWT | Feedback trends |
| `GET /api/insights/summary` | Optional `from`, `to` | Owner bearer JWT | Cached summary for the requested period |
| `POST /api/insights/summary/generate` | Optional `from`, `to`, `force` (default `false`) | Owner bearer JWT | Generates or refreshes an evidence-grounded summary |
| `POST /api/google-reviews/{reviewId}/response-drafts` | Optional `{"tone":"PROFESSIONAL"}` | Owner bearer JWT | Generates a proposed reply for an imported review |
| `GET /api/google-reviews/{reviewId}/response-drafts` | `page` (default `0`), `size` (default `20`) | Owner bearer JWT | Lists drafts for an owner's imported review |
| `GET /api/review-response-drafts/{draftId}` | — | Owner bearer JWT | Reads an owner's draft |
| `PUT /api/review-response-drafts/{draftId}` | `{"responseText":"..."}` | Owner bearer JWT | Edits a draft before approval |
| `POST /api/review-response-drafts/{draftId}/approve` | — | Owner bearer JWT | Approves a draft and records approver/time; does not publish |
| `POST /api/review-response-drafts/{draftId}/reject` | Optional `{"reason":"..."}` | Owner bearer JWT | Rejects a draft |

Review-response generation requires a review already persisted for the authenticated
business. AI rate limiting applies to summary and review-draft generation per source IP
and application instance. This release provides an internal review-import persistence boundary only:
it does not synchronize with Google, expose a Google import endpoint, or publish replies.
Approved replies remain drafts for manual handling; deployments with multiple instances
should add a shared limit for summary and review-draft generation.
Migration `V4__add_ai_analysis_insights_and_review_drafts.sql` adds the analysis,
summary, imported-review, and response-draft schema without modifying prior migrations.

### n8n / WhatsApp integration

The integration is disabled unless `N8N_ENABLED=true`. When enabled, the application
claims durable outbox events with database row locks, calls the configured n8n webhook
outside the database transaction, and retries transient failures with bounded backoff.
The webhook receives only an event ID, request ID, scheduled time, and event type; it
does not receive phone numbers or feedback tokens in the scheduling payload. The n8n
workflow should wait until `scheduledAt`, then fetch delivery data over HTTPS:

```text
GET /api/internal/feedback-requests/{requestId}/delivery?eventId={eventId}
X-Internal-Api-Key: <N8N_INTERNAL_API_KEY>
```

That protected response contains the customer phone and opaque feedback link. Treat it
as sensitive, do not log it, and do not expose the internal key in a browser. After
WhatsApp provider updates, n8n can POST a JSON callback to
`/api/internal/webhooks/whatsapp/delivery` with
`X-Webhook-Secret: <N8N_CALLBACK_SECRET>`. The body includes `eventId`,
`feedbackRequestId`, `deliveryEventId` (the schedule event ID returned by the delivery
details endpoint), `status` (`SENT`, `DELIVERED`, or `FAILED`), optional
`providerMessageId`, and ISO-8601 `eventTimestamp`. Event IDs are replay-protected;
stale/out-of-order callbacks cannot regress delivery state. Secrets must be shared with
the workflow through a secret manager, and service-to-service traffic must use HTTPS.
Configure and test these n8n workflow steps before enabling the integration.

Migration `V3__create_feedback_foundation.sql` adds the question, invitation,
conversation, feedback, answer, outbox, and callback-idempotency tables. Tenant-consistent
composite foreign keys protect business boundaries in the database; Hibernate remains
in `validate` mode.

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

## Phase 6 — Dashboard and Google Review Flow

### Dashboard architecture and calculations

Dashboard endpoints resolve the business only from the authenticated JWT owner. They
reuse the Phase 5 PostgreSQL insight aggregates, persisted feedback analyses, and cached
summary implementation. Rating metrics come directly from completed private feedback;
sentiment, topic, aspect, issue, and highlight metrics include successfully analyzed
records only. Unanalyzed feedback is reported separately as pending or failed rather than
being treated as neutral. Google reviews are separate public records and are not included
in private feedback metrics.

Dashboard time filters are ISO-8601 instants in UTC, use a start-inclusive/end-exclusive
range, and default to the preceding 30 days. The maximum range defaults to 366 days.
The overview returns rating percentages over all completed feedback and sentiment
percentages over analyzed feedback. Its comparison uses the immediately preceding
equal-duration interval; it is omitted if that interval has no feedback. Trend buckets
are daily, ISO-weekly (Monday UTC start), or monthly, and missing buckets are returned
with zero counts. Topic/aspect mentions and issue reports count distinct feedback records.
An issue is called recurring only after the configured minimum number of distinct
feedback reports (default two); severity remains the AI-assigned severity and is not
inferred from sentiment. Activity counts use request/conversation lifecycle state and
creation timestamps, while analysis counts use the feedback submission period.

### Dashboard APIs

All endpoints below require the owner's bearer JWT, derive the business from the JWT
subject, and return the standard `ApiResponse` envelope. Optional `from` and `to` query
parameters accept ISO-8601 instants. Invalid ranges, enum filters, or limits return a
safe `400`; missing business onboarding returns `404`; another business's data is never
selected.

| Method and path | Optional query | Response data |
| --- | --- | --- |
| `GET /api/dashboard/overview` | `from`, `to` | Period, completed feedback count and average rating, rating counts/percentages, sentiment counts/percentages, analysis coverage, and previous-period comparison when available |
| `GET /api/dashboard/trends` | `from`, `to`, `interval` (`DAILY`, `WEEKLY`, `MONTHLY`; default `DAILY`) | Time buckets with start/end, feedback count, average rating, analyzed sentiment counts, and analysis coverage |
| `GET /api/dashboard/topics` | `from`, `to`, `limit` (default `20`, max `100`), `sentiment` | Normalized topic, distinct feedback mentions, sentiment breakdown, and percentage of analyzed feedback |
| `GET /api/dashboard/aspects` | `from`, `to`, `limit` (default `20`, max `100`) | Aspect name, distinct mentions, sentiment breakdown, average analysis confidence, and period |
| `GET /api/dashboard/issues` | `from`, `to`, `limit` (default `20`, max `100`), `severity` (`LOW`, `MEDIUM`, `HIGH`) | Recurring issue, distinct reports, configured severity distribution, related topics, first/latest report times, and sentiment breakdown |
| `GET /api/dashboard/highlights` | `from`, `to`, `limit` (default `20`, max `100`) | Separate positive and negative aspect observations with grounded explanation and distinct supporting-feedback count |
| `GET /api/dashboard/summary` | `from`, `to` | Persisted AI summary, observations, concerns, recommendations, period/count metadata, and analysis coverage; `404` if no summary exists |
| `POST /api/dashboard/summary/generate` | `from`, `to`, `force` (default `false`) | Creates or refreshes the cached evidence-grounded summary; requires at least three analyzed feedback records |
| `GET /api/dashboard/activity` | `from`, `to` | Request lifecycle, conversation status, and pending/failed analysis counts |

Summary generation reuses Phase 5's AI provider and deterministic metrics. The cache is
keyed by exact business/date range and is reused only while feedback and analyzed-record
counts, latest feedback time, and cooldown remain current. A database-backed lease
prevents concurrent generation for the same business/period across application instances.
AI provider errors return a safe `503` from summary generation; dashboard metrics do not
depend on summary availability. Migration `V5__dashboard_summary_generation_guard.sql`
adds the cache freshness count and generation lease table; Hibernate remains in `validate`
mode.

### Google Review facilitation and independent flows

Owners configure the review link through the existing
`PATCH /api/business/me/google-review-url` request body
`{"googleReviewUrl":"https://..."}`. A link is optional. Configured URLs must use HTTPS
and match a supported Google review destination (Google Maps review/place URL, Google
local write-review URL with a place ID, `g.page` review URL, or supported Google Maps
short link); arbitrary hosts, unsafe schemes, user-info, and nonstandard ports are
rejected. Copy the review URL from the business's Google profile. URL validation checks
the destination host/path but does not verify business ownership with Google.

After `POST /api/public/feedback/{token}/submit` succeeds, the existing confirmation data
also includes `feedbackCompleted`, `conversationCompleted`, `completionMessage`,
`googleReviewAvailable`, `googleReviewUrl`, and `reviewInvitationMessage`. The link is
included independent of rating or sentiment; absent configuration returns
`googleReviewAvailable: false` while still confirming completion. The customer is invited
to post a voluntary, genuine public review. Completion creates only private feedback and
its internal analysis outbox job; it does not wait for AI, record a link click, or create
a `GoogleReview`.

The authenticated owner QR endpoint `GET /api/business/me/google-review-qr` returns a
private, no-store `image/png` generated on demand from the configured review URL alone.
It encodes no feedback token or customer data. No separate QR file is persisted.

The two flows remain independent:

- **Internal:** completed private feedback → asynchronous analysis → dashboard metrics.
- **Google:** completed feedback → configured review link/QR → customer's independent
  decision whether to post a public review.

Ignoring the link does not affect internal analysis. AI failure does not block feedback
completion or link display. An actual `GoogleReview` record must come from a verified,
authorized import; this release has no Google Business Profile synchronization or reply
publishing integration, and does not fabricate Google review metrics.

### Phase 6 testing

Run all unit tests and the application verification from the `project` directory using
`.\mvnw.cmd clean verify`. Focused dashboard and URL/QR/feedback-flow tests can be run
with `.\mvnw.cmd "-Dtest=DashboardServiceTests,GoogleReviewFlowTests" test`. PostgreSQL
schema and MockMvc integration tests require Docker through Testcontainers; they are
skipped when Docker is unavailable. QR generation tests decode the PNG and compare its
payload with the configured URL. Configure an owner's Google URL with the PATCH endpoint
before requesting the QR or expecting a link in the completion response.

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
- Add shared/distributed rate limiting for public feedback, registration, and OTP endpoints
  before scaling; feedback limiting is currently per instance, and OTP attempts plus
  per-account resend cooldown are enforced locally.
- Configure deployment, backups, monitoring, alerting, and operational incident procedures.
- Provision and rotate the dedicated phone encryption and HMAC secrets; implement a
  reviewed key-rotation procedure before changing the active encryption key.
- Account linking, phone OTP, and authenticated Google-linking are intentionally not part
  of this phase.
