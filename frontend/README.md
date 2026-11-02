# Review Engine frontend

The frontend is a Next.js App Router application using TypeScript and SCSS
Modules. It communicates with the Spring Boot backend through HTTP APIs; it
does not connect directly to PostgreSQL.

## Backend API configuration

Copy `.env.example` to `.env.local` and set `NEXT_PUBLIC_API_BASE_URL` to the
Spring Boot API base URL (for example, `https://api.example.com`). For Google
Sign-In, set `NEXT_PUBLIC_GOOGLE_CLIENT_ID` to the public OAuth client ID and
configure the same `GOOGLE_CLIENT_ID` on the backend. The backend must allow
the frontend's exact origin in `CORS_ALLOWED_ORIGINS`. These frontend values
are public and must not contain credentials or secrets.
The resend countdown defaults to the backend's documented 60-second
`OTP_RESEND_COOLDOWN_SECONDS`; set `NEXT_PUBLIC_OTP_RESEND_COOLDOWN_SECONDS` to
the same value if that backend setting is overridden. Add the frontend origin
to the Google OAuth client's authorized JavaScript origins.
Email registration and verification also require the backend's Spring Mail
settings; the frontend does not send verification email itself.

## Customer management

Authenticated business owners can use `/customers` to search and page through
their customers, `/customers/new` to add one, `/customers/[customerId]` to view
details, and `/customers/[customerId]/edit` to update it. The frontend uses
`POST /api/customers`, `GET /api/customers` (name search and backend pagination),
`GET /api/customers/{customerId}`, and `PUT /api/customers/{customerId}` through
the centralized API client. Ownership is determined by the backend from the
authenticated account. Phone numbers are validated and normalized by the
backend using its configured `PHONE_DEFAULT_REGION`; phone data is not stored
in browser storage. Customer deletion is not supported by the backend.

## Transaction management

Authenticated business owners can use `/transactions` to view and paginate
transactions, `/transactions/new` to record a completed transaction, and
`/transactions/[transactionId]` to view details. The frontend uses
`GET /api/transactions` (backend pagination and reference-code filtering),
`POST /api/transactions`, and `GET /api/transactions/{transactionId}` through
the centralized API client. Creation sends `customerId` and an optional
`referenceCode` and optional offset-aware `completedAt`; the backend assigns
`COMPLETED` status and the business scope.
There is no amount field in the current transaction contract. Transaction
update and deletion are not supported.

## Feedback collection

Authenticated business owners can manage questions at `/feedback/questions`,
create and monitor invitations at `/feedback/requests`, and open request details
at `/feedback/requests/[requestId]`. Request creation requires a completed
transaction and displays the feedback link only when it is returned by the
backend at creation time. The request list and detail contracts do not return
that link again. Delivery is asynchronous and depends on the backend's
configured delivery integration.

Customers open the opaque URL supplied by the backend at `/feedback/[token]`.
That public page validates the link, starts or resumes its conversation, and
submits the backend-defined typed answers. Public feedback calls do not attach
the owner's bearer token. Expired links are reported separately from links
that are invalid or unavailable. If final submission has an ambiguous result,
the page checks the public link's authoritative completion state without
replaying the submission. Still-valid completed links recover the completion
message and backend-returned review URL after reload; expired links remain
unavailable. The page does not automatically resubmit. Google review links and
invitations are shown only when the backend returns them after confirmed
feedback completion.

## Individual feedback analysis

The owner-only route `/feedback/analysis/[feedbackId]` retrieves one analysis
through `GET /api/feedback/{feedbackId}/analysis` and can queue a retry through
`POST /api/feedback/{feedbackId}/analysis/retry`. Both use bearer
authentication and are scoped by the backend to the authenticated owner's
business. Final feedback submission automatically queues analysis; the frontend
does not start a duplicate job. The page shows the returned lifecycle status,
sentiment and model self-assessed confidence, summary, topics, aspects, issues,
highlights, and improvement opportunities. No analysis data is shown on the
public customer feedback page.

The backend returns `PENDING`, `PROCESSING`, or `FAILED` job states and
`COMPLETED` when a stored analysis is available. `PROCESSED` without an analysis
payload is treated as unavailable, not as a completed result. The page offers
manual status refresh rather than automatic polling. Retry is available only
for `FAILED` jobs and the backend accepts it only in that state; after queuing,
the page reloads status. AI processing requires backend AI configuration, which
is disabled by default.

The owner-scoped feedback request list and detail responses include the actual
feedback UUID when a submission exists. The request UI links to analysis only
when that UUID is present; request IDs are never used as feedback IDs.

## Dashboard analytics

Authenticated business owners can view backend-scoped analytics at
`/dashboard`. The page loads the dashboard overview, weekly trends, topics,
aspects, recurring issues, highlights, saved summary, and activity through the
centralized API client. Leave both date filters blank to use the backend's
default period, or choose a UTC calendar-day range of up to 366 days. The
selected final day is included by sending the following midnight as the
exclusive `to` instant. Owners can generate or refresh a summary from the
backend when at least three analyses are available. The page leaves the
backend's force flag unset and reports AI configuration, rate-limit, and
generation errors instead of fabricating a summary. Request/conversation and
analysis activity have different backend date semantics, so they are not
presented as interchangeable event counts.

The root route directs unauthenticated users to sign-in, users without a
business to onboarding, and business owners to the dashboard. Owner pages share
responsive navigation and sign-out controls; when the server cannot confirm
logout, the login page explicitly warns that server-side sign-out was not
confirmed.

## Google review facilitation and response drafts

After final feedback and conversation completion are confirmed by the backend,
the public `/feedback/[token]` page shows the exact Google review URL and
invitation returned by the backend, irrespective of the customer's rating.
The page does not auto-navigate, construct a review URL, or expose owner-only
QR data to customers.

Owners can manage drafts at
`/google-reviews/[reviewId]/response-drafts` only when they have an imported
Google review UUID already known to the backend. The page first verifies that
review through the backend's draft-list endpoint, then supports backend draft
generation, editing, approval, and rejection. There is currently no browser
API for listing/importing Google reviews, synchronizing Google reviews, or
publishing replies. Approval records an owner decision; it does not publish to
Google. Draft generation also depends on the backend's AI configuration.

For local HTTP development, configure the backend using its documented
environment variables, including `COOKIE_SECURE=false` where appropriate.
Keep secure cookies and explicit CORS origins in deployed environments. The
backend contract inventory is in
[`docs/backend-api-contracts.md`](./docs/backend-api-contracts.md).

## Development

```sh
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in a browser.

## Validation

```sh
npm run typecheck
npm run lint
npm test
npm run build
```

## Source layout

- `src/app/` contains routes, layouts, and route-level styles.
- `src/features/` owns feature-specific UI, hooks, services, schemas, and types.
- `src/components/` contains shared UI and layout components.
- `src/lib/api/` and `src/lib/auth/` contain shared API and authentication
  infrastructure.
- `src/providers/`, `src/hooks/`, `src/styles/`, and `src/types/` contain
  application-wide providers, hooks, design styles, and shared types.
