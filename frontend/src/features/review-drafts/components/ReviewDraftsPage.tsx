"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import {
  approveReviewDraft,
  editReviewDraft,
  generateReviewDraft,
  listReviewDrafts,
  rejectReviewDraft,
} from "../services/review-draft.service";
import type {
  ReviewResponseDraft,
  ReviewResponseTone,
} from "../types/review-draft.types";
import styles from "./review-drafts.module.scss";

type DraftPageState =
  | { status: "loading" }
  | { status: "ready"; drafts: ReviewResponseDraft[]; pageNumber: number; totalPages: number; hasNext: boolean; hasPrevious: boolean }
  | { status: "error"; message: string };

const TONES: ReviewResponseTone[] = [
  "PROFESSIONAL",
  "FRIENDLY",
  "WARM",
  "FORMAL",
];

function DraftCard({
  draft,
  busy,
  onEdit,
  onApprove,
  onReject,
}: {
  draft: ReviewResponseDraft;
  busy: boolean;
  onEdit: (responseText: string) => Promise<void>;
  onApprove: () => Promise<void>;
  onReject: (reason: string) => Promise<void>;
}) {
  const [responseText, setResponseText] = useState(draft.responseText);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submitEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    try {
      await onEdit(responseText);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The draft could not be saved.");
    }
  }

  async function approve() {
    setError(null);
    try {
      await onApprove();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The draft could not be approved.");
    }
  }

  async function reject() {
    setError(null);
    try {
      await onReject(reason);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The draft could not be rejected.");
    }
  }

  const editable = draft.status === "GENERATED" || draft.status === "EDITED";

  return (
    <article className={styles.card}>
      <header className={styles.draftHeader}>
        <div>
          <span className={styles.badge}>{draft.status}</span>
          <p className={styles.muted}>
            Draft generated {new Date(draft.generatedAt).toLocaleString()}
          </p>
        </div>
        <span className={styles.badge}>{draft.tone}</span>
      </header>
      <section className={styles.review} aria-label="Imported Google review">
        <h2 className={styles.heading}>
          {draft.reviewerDisplayName ?? "Google reviewer"}
        </h2>
        <p>Rating: {draft.reviewRating} / 5</p>
        {draft.reviewText ? <blockquote>{draft.reviewText}</blockquote> : <p className={styles.muted}>No review text was provided.</p>}
        <p className={styles.muted}>
          Reviewed <time dateTime={draft.reviewDate}>{new Date(draft.reviewDate).toLocaleDateString()}</time>
        </p>
      </section>
      <section className={styles.stack}>
        <div>
          <h3 className={styles.subheading}>Generated draft</h3>
          <p className={styles.generatedText}>{draft.generatedText}</p>
        </div>
        {editable ? (
          <form className={styles.stack} onSubmit={submitEdit}>
            <label className={styles.field}>
              <span className={styles.label}>Response text</span>
              <textarea
                className={styles.textarea}
                value={responseText}
                maxLength={2000}
                required
                onChange={(event) => setResponseText(event.target.value)}
              />
            </label>
            <button className={styles.secondaryButton} type="submit" disabled={busy}>
              {busy ? "Saving…" : "Save edit"}
            </button>
          </form>
        ) : (
          <div>
            <h3 className={styles.subheading}>Current response</h3>
            <p className={styles.generatedText}>{draft.responseText}</p>
          </div>
        )}
      </section>
      {draft.status === "APPROVED" ? (
        <p className={styles.notice} role="status">
          Approved by the owner; this response has not been published to Google.
        </p>
      ) : null}
      {draft.status === "REJECTED" ? (
        <p className={styles.notice} role="status">
          Rejected{draft.rejectionReason ? `: ${draft.rejectionReason}` : "."}
        </p>
      ) : null}
      {draft.publishedAt ? (
        <p className={styles.notice}>
          Backend publication timestamp:{" "}
          <time dateTime={draft.publishedAt}>{new Date(draft.publishedAt).toLocaleString()}</time>
        </p>
      ) : null}
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      {editable ? (
        <div className={styles.actions}>
          <button
            className={styles.button}
            type="button"
            disabled={busy}
            onClick={() => void approve()}
          >
            {busy ? "Updating…" : "Approve draft"}
          </button>
          <label className={styles.field}>
            <span className={styles.label}>Rejection reason (optional)</span>
            <input
              className={styles.input}
              value={reason}
              maxLength={1000}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          <button
            className={styles.secondaryButton}
            type="button"
            disabled={busy}
            onClick={() => void reject()}
          >
            Reject draft
          </button>
        </div>
      ) : null}
    </article>
  );
}

function DraftsContent({ reviewId }: { reviewId: string }) {
  const [pageNumber, setPageNumber] = useState(0);
  const [revision, setRevision] = useState(0);
  const key = `${reviewId}:${pageNumber}:${revision}`;
  const [loadResult, setLoadResult] = useState<{
    key: string;
    state: Exclude<DraftPageState, { status: "loading" }>;
  } | null>(null);
  const [tone, setTone] = useState<ReviewResponseTone>("PROFESSIONAL");
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [generationUncertain, setGenerationUncertain] = useState(false);
  const actionInFlight = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void listReviewDrafts(reviewId, {
      page: pageNumber,
      size: 20,
      signal: controller.signal,
    })
      .then((response) => {
        if (active) {
          setLoadResult({
            key,
            state: {
              status: "ready",
              drafts: response.content,
              pageNumber: response.pageNumber,
              totalPages: response.totalPages,
              hasNext: response.hasNext,
              hasPrevious: response.hasPrevious,
            },
          });
        }
      })
      .catch((cause: unknown) => {
        if (active && !controller.signal.aborted) {
          setLoadResult({
            key,
            state: {
              status: "error",
              message:
                cause instanceof ApiError
                  ? cause.message
                  : "Drafts could not be loaded for this imported review.",
            },
          });
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [key, pageNumber, reviewId]);

  const state =
    loadResult?.key === key
      ? loadResult.state
      : ({ status: "loading" } as const);

  async function runAction(actionId: string, operation: () => Promise<unknown>) {
    if (actionInFlight.current) return;
    actionInFlight.current = true;
    setBusyAction(actionId);
    setActionError(null);
    try {
      await operation();
      setRevision((value) => value + 1);
    } catch (cause) {
      if (
        actionId === "generate" &&
        (!(cause instanceof ApiError) ||
          cause.code === "NETWORK_ERROR" ||
          cause.code === "TIMEOUT" ||
          (cause instanceof ApiError && (cause.status ?? 0) >= 500))
      ) {
        setGenerationUncertain(true);
      }
      setActionError(
        cause instanceof Error ? cause.message : "The draft action could not be completed.",
      );
    } finally {
      actionInFlight.current = false;
      setBusyAction(null);
    }
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Google review response drafts</p>
            <h1 className={styles.title}>Draft management</h1>
            <p className={styles.description}>
              Only use an imported review UUID already known to the backend.
              Review discovery and import are not available here.
            </p>
          </div>
        </header>
        <section className={styles.section}>
          <p><strong>Imported review ID:</strong> <code>{reviewId}</code></p>
          {state.status === "loading" ? (
            <p role="status">Verifying the imported review and loading drafts…</p>
          ) : state.status === "error" ? (
            <>
              <p className={styles.error} role="alert">{state.message}</p>
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => setRevision((value) => value + 1)}
              >
                Retry loading
              </button>
            </>
          ) : (
            <>
              <label className={styles.field}>
                <span className={styles.label}>Draft tone</span>
                <select
                  className={styles.input}
                  value={tone}
                  onChange={(event) => setTone(event.target.value as ReviewResponseTone)}
                >
                  {TONES.map((option) => (
                    <option key={option} value={option}>{option}</option>
                  ))}
                </select>
              </label>
              <button
                className={styles.button}
                type="button"
                disabled={busyAction !== null || generationUncertain}
                onClick={() =>
                  void runAction("generate", () => generateReviewDraft(reviewId, tone))
                }
              >
                {busyAction === "generate"
                  ? "Generating draft…"
                  : generationUncertain
                    ? "Generation status unknown"
                    : "Generate response draft"}
              </button>
              {generationUncertain ? (
                <p className={styles.notice} role="alert">
                  The backend may have created a draft, but the response was not
                  confirmed. Reload drafts before attempting generation again.
                </p>
              ) : null}
              <p className={styles.muted}>
                Draft generation uses the backend AI service and an existing
                imported review. It does not publish a response.
              </p>
            </>
          )}
        </section>
        {actionError ? <p className={styles.error} role="alert">{actionError}</p> : null}
        {state.status === "ready" ? (
          state.drafts.length === 0 ? (
            <section className={styles.section}>
              <h2 className={styles.heading}>No response drafts yet</h2>
              <p className={styles.muted}>
                The backend confirmed this review is available, but no drafts
                have been generated for it.
              </p>
            </section>
          ) : (
            <>
              <div className={styles.list}>
                {state.drafts.map((draft) => (
                  <DraftCard
                    key={draft.id}
                    draft={draft}
                    busy={busyAction === draft.id}
                    onEdit={(responseText) =>
                      runAction(draft.id, () => editReviewDraft(draft.id, responseText))
                    }
                    onApprove={() =>
                      runAction(draft.id, () => approveReviewDraft(draft.id))
                    }
                    onReject={(reason) =>
                      runAction(draft.id, () =>
                        rejectReviewDraft(draft.id, reason.trim() || undefined),
                      )
                    }
                  />
                ))}
              </div>
              {state.totalPages > 1 ? (
                <nav className={styles.pagination} aria-label="Response draft pages">
                  <button
                    className={styles.secondaryButton}
                    type="button"
                    disabled={!state.hasPrevious}
                    onClick={() => setPageNumber((value) => Math.max(0, value - 1))}
                  >
                    Previous
                  </button>
                  <span>Page {state.pageNumber + 1} of {state.totalPages}</span>
                  <button
                    className={styles.secondaryButton}
                    type="button"
                    disabled={!state.hasNext}
                    onClick={() => setPageNumber((value) => value + 1)}
                  >
                    Next
                  </button>
                </nav>
              ) : null}
            </>
          )
        ) : null}
        <p className={styles.disclaimer}>
          Approval records the owner&apos;s decision only. No Google publishing API
          is available, and this frontend never claims an approved response was
          published.
        </p>
      </div>
    </main>
  );
}

export function ReviewDraftsPage({ reviewId }: { reviewId: string }) {
  return (
    <BusinessRouteGate>
      <DraftsContent reviewId={reviewId} />
    </BusinessRouteGate>
  );
}
