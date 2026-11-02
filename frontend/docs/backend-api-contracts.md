# Spring Boot API contract inventory

This inventory records the backend contracts inspected for Phase 7. The Spring
Boot controllers and DTOs are authoritative; this document is a frontend
integration index, not a replacement for them. Do not derive contracts from
frontend expectations.

## Contract-wide behavior

- The frontend API base URL is configured with the public
  `NEXT_PUBLIC_API_BASE_URL` environment variable; do not put credentials or
  secrets in it. The backend uses explicit allowed origins.
- Unless stated otherwise, responses use
  `ApiResponse<T>`: `{ "success": boolean, "message": string, "data": T |
  null, "timestamp": ISO-8601 instant }`. `PageResponse<T>` data contains
  `content`, `pageNumber` (zero-based), `pageSize`, `totalElements`,
  `totalPages`, `hasNext`, and `hasPrevious`.
- Errors use the same JSON envelope with `success: false` and `data: null`.
  Validation returns HTTP 400 with a safe message (at most the first invalid
  field is identified); auth errors return 401, authorization errors 403,
  inaccessible/missing resources commonly 404, data conflicts 409, and
  unexpected failures 500 with a sanitized message. Some business or provider
  failures have specific status codes described in the backend README.
- Owner APIs require `Authorization: Bearer <accessToken>`. The server derives
  owner and business scope from the verified JWT; clients must not supply
  owner/business IDs as authorization inputs.
- ISO timestamps use UTC instants unless the request explicitly accepts an
  offset-aware `OffsetDateTime`. Page indexes are zero-based.
- Internal n8n routes are service-to-service contracts, not browser APIs.
- `GET /api/business/me/google-review-qr` is a private `image/png` response
  with `Cache-Control: no-store`, not an `ApiResponse` JSON envelope.
- `GET /api/test` is an authenticated diagnostic endpoint returning plain
  text, not an application feature contract.

## Authentication and onboarding

| Method and path | Auth | Request body / query | Success data | Frontend consumer |
|---|---|---|---|---|
| `POST /api/auth/register` | Public | `RegisterRequest`: `name`, `email`, `password` (password 12–72 chars) | `RegisterResponse`: `emailVerificationRequired`, `developmentOtp` (dev-only when explicitly enabled); HTTP 201; pending account only, no token or business | Auth registration |
| `POST /api/auth/verify-email` | Public | `VerifyEmailRequest`: `email`, six-digit `otp` | `null` (`ApiResponse<Void>`) | Email verification |
| `POST /api/auth/resend-otp` | Public | `ResendOtpRequest`: `email` | `ResendOtpResponse`; generic response; `developmentOtp` only in configured dev mode | OTP resend |
| `POST /api/auth/login` | Public | `LoginRequest`: `email`, `password` | `AuthResponse`: `accessToken`, `tokenType`, `user` | Password login |
| `POST /api/auth/google` | Public | `GoogleLoginRequest`: Google `idToken` | Same `AuthResponse` as password login | Google Sign-In |
| `POST /api/auth/refresh` | Refresh cookie + CSRF | No body; `refresh_token` and `XSRF-TOKEN` cookies plus `X-XSRF-TOKEN` header | New `AuthResponse`; rotates the refresh cookie | Session restoration / token refresh |
| `POST /api/auth/logout` | Refresh cookie + CSRF | No body; same cookie/header requirements as refresh | `null`; revokes refresh token and clears cookies | Logout |
| `GET /api/auth/me` | Bearer JWT | No body/query | `CurrentUserResponse`: `id`, `name`, `email`, `accountStatus`, `emailVerified`, `hasBusiness` | Session/user state and onboarding gate |
| `POST /api/business` | Bearer JWT | `BusinessRequest`: `name`, `category`, `address`, IANA `timezone`, optional `googleReviewUrl` | `BusinessResponse`; HTTP 201 | Business onboarding |
| `GET /api/business/me` | Bearer JWT | No body/query | `BusinessResponse`; 404 if onboarding is incomplete | Business profile |
| `PUT /api/business/me` | Bearer JWT | Same `BusinessRequest` fields as create | Updated `BusinessResponse` | Business profile editing |
| `PATCH /api/business/me/google-review-url` | Bearer JWT | `GoogleReviewUrlRequest`: `googleReviewUrl` | Updated `BusinessResponse` | Review URL setup |
| `GET /api/business/me/google-review-qr` | Bearer JWT | No body/query | Private PNG bytes, no-store | Business review QR display |

Registration is intentionally separate from business creation. Google login
accepts a Google ID token; the backend validates it against configured audience,
issuer, expiry, and verified email. Account linking is not implemented.

## Customers and transactions

| Method and path | Auth | Request body / query | Success data | Frontend consumer |
|---|---|---|---|---|
| `POST /api/customers` | Bearer JWT | `CreateCustomerRequest`: `name`, `phone` | `CustomerResponse` (`id`, name, normalized phone, created/updated times); HTTP 201 | Customer create |
| `GET /api/customers` | Bearer JWT | `page` (0), `size` (20, max 100), `sort` (`createdAt,desc`), optional `name` | `PageResponse<CustomerSummaryResponse>`; summary omits phone | Customer search/list/pagination |
| `GET /api/customers/{customerId}` | Bearer JWT | UUID path parameter | `CustomerResponse` | Customer detail |
| `PUT /api/customers/{customerId}` | Bearer JWT | `UpdateCustomerRequest`: `name`, `phone` | `CustomerResponse` | Customer edit |
| `POST /api/transactions` | Bearer JWT | `CreateTransactionRequest`: `customerId`, optional `referenceCode`, optional offset-aware `completedAt` | `TransactionResponse`; HTTP 201; status is assigned as `COMPLETED` by backend | Record completed transaction |
| `GET /api/transactions` | Bearer JWT | `page` (0), `size` (20, max 100), `sort` (`completedAt,desc`), optional `customerId`, `from`, `to`, `status`, `referenceCode` | `PageResponse<TransactionResponse>` | Transaction list/filter/pagination |
| `GET /api/transactions/{transactionId}` | Bearer JWT | UUID path parameter | `TransactionResponse` | Transaction detail |

Customer phones are returned only in owner-scoped detail/create/update responses;
list summaries omit them. Customer deletion and transaction update/deletion are
not implemented. Transaction creation ignores client status and always creates a
completed transaction. `status` filtering accepts only `COMPLETED`.

## Feedback questions, requests, and public feedback

| Method and path | Auth | Request body / query | Success data | Frontend consumer |
|---|---|---|---|---|
| `POST /api/feedback-questions` | Bearer JWT | `FeedbackQuestionRequest`: `questionText`, `questionType` (`TEXT`, `RATING`, `YES_NO`), `required`, `displayOrder`, `active` | `FeedbackQuestionResponse`; HTTP 201 | Question creation |
| `GET /api/feedback-questions` | Bearer JWT | Optional `active`; `page` (0), `size` (50, max 100) | `PageResponse<FeedbackQuestionResponse>` | Question list/filter |
| `GET /api/feedback-questions/{questionId}` | Bearer JWT | UUID path parameter | `FeedbackQuestionResponse` | Question detail |
| `PUT /api/feedback-questions/{questionId}` | Bearer JWT | Same `FeedbackQuestionRequest` fields as create | `FeedbackQuestionResponse` | Question editing |
| `PATCH /api/feedback-questions/{questionId}/status` | Bearer JWT | `{"active": boolean}` | `FeedbackQuestionResponse` | Activate/deactivate |
| `POST /api/feedback-requests` | Bearer JWT | `CreateFeedbackRequest`: `transactionId`, optional `scheduledAt` | `FeedbackRequestResponse`; HTTP 201, includes owner-visible `feedbackLink` | Schedule request |
| `GET /api/feedback-requests` | Bearer JWT | Optional `status`; `page` (0), `size` (20, max 100) | `PageResponse<FeedbackRequestResponse>` | Request list/status |
| `GET /api/feedback-requests/{requestId}` | Bearer JWT | UUID path parameter | `FeedbackRequestResponse` | Request detail |
| `PATCH /api/feedback-requests/{requestId}/cancel` | Bearer JWT | No body | `FeedbackRequestResponse` | Cancel eligible request |
| `POST /api/feedback-requests/{requestId}/retry` | Bearer JWT | No body | `FeedbackRequestResponse` | Retry eligible failed request |
| `GET /api/public/feedback/{token}` | Public token | Opaque token path parameter | Active link: business name, expiry, active questions, null completion. Completed and still-valid link: safe completion confirmation and no questions. | Public feedback page/token validation and completion recovery |
| `POST /api/public/feedback/{token}/conversation` | Public token | No body | `FeedbackConversationResponse` | Start or resume conversation |
| `GET /api/public/feedback/{token}/conversation` | Public token | No body | `FeedbackConversationResponse`: status, times, messages, questions | Conversation restoration |
| `POST /api/public/feedback/{token}/conversation/messages` | Public token | `CustomerMessageRequest`: `content`, optional `rating` (1–5) | `FeedbackConversationResponse`; HTTP 201; AI follow-up is optional | Customer conversation |
| `POST /api/public/feedback/{token}/submit` | Public token | `FeedbackSubmissionRequest`: `rating` (1–5), optional `comment`, optional `answers[]` (`questionId`, typed answer fields) | `FeedbackConfirmationResponse`: completion fields and optional Google URL/invitation | Final submission and review completion |

The public token is an expiring bearer capability in the URL; do not log it or
send it to owner-only endpoints. Public routes are IP-rate-limited per backend
instance. Page validation permits an unexpired completed token to recover a
customer-safe confirmation; it exposes neither submitted feedback nor private
analysis, and conversation start/read/write operations continue rejecting
completed tokens. A recovered review URL reflects the business's currently
configured valid URL. Feedback submission creates internal analysis work
independently of the Google review invitation. The configured review link is
returned regardless of rating/sentiment; it is absent only when the business
has no configured URL. The response does not imply a Google review was posted.
Owner feedback request list responses expose the nullable actual feedback UUID
through a batch lookup, and detail responses look it up within the owner's
scoped request. This lets the analysis route be linked without confusing
request IDs with feedback IDs.

## Analysis, insights, and dashboard

All routes in this section require bearer auth and are scoped to the current
owner's business. `from` and `to` are optional ISO-8601 instants; backend period
defaults/maximums and AI prerequisites are documented in
[the backend README](../../project/README.md).

| Method and path | Request body / query | Success data | Frontend consumer |
|---|---|---|---|
| `GET /api/feedback/{feedbackId}/analysis` | Feedback UUID path parameter | `FeedbackAnalysisStatusResponse`: `feedbackId`, `status`, nullable `analysis`, `safeError`, `analyzedAt` | Individual feedback analysis state |
| `POST /api/feedback/{feedbackId}/analysis/retry` | Feedback UUID path parameter; no body | `null` | Retry a failed individual analysis |
| `GET /api/insights/overview` | Optional `from`, `to` | `InsightsOverviewResponse` | Legacy/general feedback metrics |
| `GET /api/insights/topics` | Optional `from`, `to` | `List<TopicInsightResponse>` | Legacy topic metrics |
| `GET /api/insights/issues` | Optional `from`, `to` | `List<IssueInsightResponse>` | Legacy issue metrics |
| `GET /api/insights/trends` | Optional `from`, `to` | `TrendInsightResponse` | Legacy trend metrics |
| `GET /api/insights/summary` | Optional `from`, `to` | `InsightSummaryResponse`; 404 when no summary exists | Legacy cached AI summary |
| `POST /api/insights/summary/generate` | Optional `from`, `to`, `force` (false) | `InsightSummaryResponse`; generation needs at least three analyzed feedback records | Legacy AI summary generation |
| `GET /api/dashboard/overview` | Optional `from`, `to` | `DashboardOverviewResponse`: rating/sentiment distributions, coverage, comparison | Dashboard overview |
| `GET /api/dashboard/trends` | Optional `from`, `to`, `interval` (`DAILY`, `WEEKLY`, `MONTHLY`; default `DAILY`) | `DashboardTrendsResponse` | Dashboard charts |
| `GET /api/dashboard/topics` | Optional `from`, `to`, `limit` (default 20, max 100), `sentiment` | `List<DashboardTopicResponse>` | Topic insights |
| `GET /api/dashboard/aspects` | Optional `from`, `to`, `limit` (default 20, max 100) | `List<DashboardAspectResponse>` | Aspect insights |
| `GET /api/dashboard/issues` | Optional `from`, `to`, `limit` (default 20, max 100), `severity` (`LOW`, `MEDIUM`, `HIGH`) | `List<DashboardIssueResponse>` | Recurring issue insights |
| `GET /api/dashboard/highlights` | Optional `from`, `to`, `limit` (default 20, max 100) | `DashboardHighlightsResponse` | Positive/negative highlights |
| `GET /api/dashboard/summary` | Optional `from`, `to` | `DashboardSummaryResponse`; 404 if absent | Persisted dashboard AI summary |
| `POST /api/dashboard/summary/generate` | Optional `from`, `to`, `force` (false) | `DashboardSummaryResponse`; generation needs at least three analyzed feedback records | Dashboard AI summary generation |
| `GET /api/dashboard/activity` | Optional `from`, `to` | `DashboardActivityResponse` | Feedback/delivery/conversation/analysis activity |

`/api/insights/*` and `/api/dashboard/*` overlap. Prefer the richer Phase 6
dashboard contracts for new dashboard work; keep the two families distinct
until consumers and compatibility needs are reviewed. AI is disabled by default.
Metrics and summaries must render the backend's actual data and coverage states,
not placeholder values.

The owner dashboard currently uses the eight `GET /api/dashboard/*` endpoints
above; it explicitly requests `WEEKLY` trends and loads them together with the
other dashboard sections. Its optional UTC date filter sends the selected
calendar start as `from` and the midnight after the selected final day as the
exclusive `to`. The UI enforces the backend's 366-day maximum and leaves both
parameters absent for the backend default. A 404 from `GET /api/dashboard/summary`
means no saved summary and is rendered as an empty state; owners can call the
supported `POST /api/dashboard/summary/generate` endpoint when at least three
analyses are available. The frontend does not set `force`, preserving backend
cache freshness, generation locking, and AI rate limits. Activity
aggregates are not temporally equivalent: request/conversation counts follow
their lifecycle/creation-time rules, whereas analysis counts use feedback
submission time.

The individual analysis payload contains `sentiment` (`POSITIVE`, `NEUTRAL`,
`NEGATIVE`, `MIXED`), `sentimentConfidence` (model self-score from 0 to 1),
`sentimentSummary`, `topics[]` (`name`, `sentiment`, `evidence`),
`aspects[]` (`name`, `sentiment`, `explanation`, `evidence`), `issues[]`
(`name`, `description`, `severity` (`LOW`, `MEDIUM`, `HIGH`), `topic`,
`evidence`), and the string arrays `positiveHighlights`, `negativeHighlights`,
and `improvementOpportunities`. The status endpoint returns `COMPLETED` with a
stored analysis; otherwise it returns the outbox state (`PENDING`,
`PROCESSING`, or `FAILED`; a processed job without a stored analysis can return
`PROCESSED`). Only a failed job can be retried; retry returns HTTP 200 with
`null`, and non-failed jobs return HTTP 409. Both endpoints are owner-bearer
authenticated and enforce business ownership. Final feedback submission
queues the analysis job automatically.

The public submission response remains customer-safe and does not expose a
feedback UUID. The owner-scoped feedback-request list/detail responses include
the nullable UUID of the actual submitted feedback; no schema migration is
needed because the feedback row is uniquely linked to its request.

## Google reviews and response drafts

| Method and path | Auth | Request body / query | Success data | Frontend consumer |
|---|---|---|---|---|
| `POST /api/google-reviews/{reviewId}/response-drafts` | Bearer JWT | Optional `GenerateReviewDraftRequest`: optional `tone` (defaults to professional) | `ReviewResponseDraftResponse`; HTTP 201 | Generate draft for an imported review |
| `GET /api/google-reviews/{reviewId}/response-drafts` | Bearer JWT | Review UUID; `page` (0), `size` (20) | `PageResponse<ReviewResponseDraftResponse>` | Draft list for a known review |
| `GET /api/review-response-drafts/{draftId}` | Bearer JWT | Draft UUID | `ReviewResponseDraftResponse` | Draft details |
| `PUT /api/review-response-drafts/{draftId}` | Bearer JWT | `EditReviewDraftRequest`: `responseText` | Updated `ReviewResponseDraftResponse` | Edit draft |
| `POST /api/review-response-drafts/{draftId}/approve` | Bearer JWT | No body | Approved `ReviewResponseDraftResponse`; explicitly not published | Approve draft |
| `POST /api/review-response-drafts/{draftId}/reject` | Bearer JWT | Optional `RejectReviewDraftRequest`: optional `reason` | Rejected `ReviewResponseDraftResponse` | Reject draft |

The database has a `GoogleReview` entity and the backend has an import
persistence service, but there is no controller for listing, retrieving, or
importing reviews and no Google synchronization or reply-publication API.
Response-draft routes require an already-known imported review ID. Review
management UI is therefore blocked until an authorized importer is available;
do not fabricate reviews. Draft approval is a human approval state only.
The frontend's `/google-reviews/{reviewId}/response-drafts` route uses the
owner-authenticated draft list to verify the supplied review before enabling
generation, editing, approval, or rejection. This does not provide review
discovery or import, and approval must not be presented as publication. Public
feedback completion continues to show the backend-returned Google URL
regardless of feedback rating/sentiment; the owner-only QR endpoint is not a
public QR facility.

## Internal delivery integration and diagnostics

| Method and path | Authentication | Request body / query | Success data | Consumer |
|---|---|---|---|---|
| `GET /api/internal/feedback-requests/{requestId}/delivery` | `X-Internal-Api-Key` | `eventId` query | `DeliveryDetailsResponse` with sensitive phone/link details | n8n only; never browser |
| `POST /api/internal/webhooks/whatsapp/delivery` | `X-Webhook-Secret` | `DeliveryCallbackRequest`: event IDs, status (`SENT`, `DELIVERED`, `FAILED`), event time, optional provider ID/failure reason | Boolean callback accepted/idempotent result | n8n/provider integration only |
| `GET /api/test` | Bearer JWT (falls under authenticated catch-all) | No body/query | Raw text `Spring Boot is working!` | Diagnostic only |
| `GET /actuator/health` | Public | No body/query | Actuator health response | Health monitoring only |

Internal endpoints must remain server-to-server. Do not embed either internal
secret in frontend code. The n8n integration is opt-in and disabled by default.

## Security and browser integration notes

- Security is stateless. Normal authenticated API requests use a short-lived
  access JWT in the `Authorization` header; the refresh token is opaque and
  HttpOnly, stored only by the browser cookie jar. Never read or persist it in
  frontend JavaScript.
- Refresh rotates the cookie; replay of an old rotated token revokes active
  refresh tokens for that account. The frontend must single-flight concurrent
  refresh requests and must not retry a non-idempotent operation after an
  ambiguous network failure.
- Refresh/logout require credentials and double-submit CSRF: read the
  non-HttpOnly `XSRF-TOKEN` cookie and send its value in `X-XSRF-TOKEN`.
  Other unsafe API operations use bearer auth and are not CSRF-protected by
  ambient cookie authentication.
- Backend CORS uses explicit configured origins, allows credentials, and
  permits `Authorization`, `Content-Type`, `X-XSRF-TOKEN`,
  `X-Internal-Api-Key`, and `X-Webhook-Secret`. Set the deployed frontend
  origin in `CORS_ALLOWED_ORIGINS`; do not use wildcard origins with credentials.
- Refresh cookie defaults are `Secure=true`, `SameSite=Strict`, path
  `/api/auth`. For local HTTP development, backend documentation instructs
  setting `COOKIE_SECURE=false`. Cross-site deployments may not send a Strict
  cookie; deployment topology and cookie policy must be agreed rather than
  weakening production defaults to make requests pass.
- Frontend session restoration must call the actual refresh endpoint with
  credentials, then keep the returned access token in the approved in-memory
  mechanism. Never assume a reload preserves that token.
- Public feedback requests are anonymous except for the token path. Render
  messages as text; do not place tokens or raw feedback text in logs.

## Backend architecture and verification references

The backend is Spring Boot 4.1.2 / Java 21 with Spring MVC, Security, JPA,
validation, PostgreSQL, and Flyway. Hibernate validates the migrated schema.
Migrations V1–V5 cover users/identities/OTP/refresh tokens/businesses,
customers/transactions, feedback questions/requests/conversations/answers and
delivery outboxes, AI analysis/insights/Google review drafts, and summary
generation coordination. Tenant ownership is enforced in service queries and
database constraints.

The backend README documents configuration and detailed lifecycle rules:
[project/README.md](../../project/README.md). Request and response DTOs are in
`project/src/main/java/review_engine/project/dto/{request,response}`; controller
and service implementations are under the same Java package.

Backend tests use JUnit/Spring test support and Testcontainers PostgreSQL.
Container-backed integration tests require Docker; the README says they are
disabled when Docker is unavailable. Existing focused tests cover auth/business,
customer/transaction, feedback token and webhook, public rate limiting,
dashboard, AI response validation, and Google review/QR/completion behavior.
At the Stage 1 audit there was no frontend unit, component, API-client, or
Playwright test harness. Stage 2 adds Vitest tests for shared API infrastructure;
these mocked tests are not backend integration tests. Do not treat this audit as
a backend test run.
