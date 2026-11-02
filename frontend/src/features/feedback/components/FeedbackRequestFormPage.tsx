"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import { listTransactions } from "../../transactions/services/transaction.service";
import type { TransactionPage } from "../../transactions/types/transaction.types";
import { createFeedbackRequest } from "../services/feedback-owner.service";
import type { FeedbackRequest } from "../types/feedback.types";
import styles from "./feedback.module.scss";

type TransactionState =
  | { key: string; status: "loading" }
  | { key: string; status: "ready"; page: TransactionPage }
  | { key: string; status: "error"; message: string };

function TransactionPicker({
  value,
  onChange,
  error,
}: {
  value: string;
  onChange: (id: string) => void;
  error?: string;
}) {
  const [pageNumber, setPageNumber] = useState(0);
  const [retry, setRetry] = useState(0);
  const key = `${pageNumber}:${retry}`;
  const [state, setState] = useState<TransactionState>({
    key: "",
    status: "loading",
  });

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void listTransactions({ page: pageNumber, signal: controller.signal })
      .then((page) => {
        if (active) setState({ key, status: "ready", page });
      })
      .catch((cause: unknown) => {
        if (active && !controller.signal.aborted) {
          setState({
            key,
            status: "error",
            message:
              cause instanceof ApiError
                ? cause.message
                : "Completed transactions could not be loaded.",
          });
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [key, pageNumber]);

  const visibleState =
    state.key === key ? state : { key, status: "loading" as const };

  if (visibleState.status === "loading") {
    return <p role="status">Loading completed transactions…</p>;
  }
  if (visibleState.status === "error") {
    return (
      <div>
        <p className={styles.error} role="alert">{visibleState.message}</p>
        <button
          className={styles.secondaryButton}
          type="button"
          onClick={() => setRetry((current) => current + 1)}
        >
          Retry transactions
        </button>
      </div>
    );
  }
  if (visibleState.page.content.length === 0) {
    return (
      <p className={styles.notice} role="status">
        No completed transactions are available to request feedback for.
      </p>
    );
  }

  return (
    <>
      <label className={styles.field}>
        <span className={styles.label}>Completed transaction *</span>
        <select
          className={styles.select}
          value={value}
          required
          aria-invalid={Boolean(error)}
          aria-describedby={error ? "transaction-select-error" : undefined}
          onChange={(event) => onChange(event.target.value)}
        >
          <option value="">Select a completed transaction</option>
          {visibleState.page.content.map((transaction) => (
            <option key={transaction.id} value={transaction.id}>
              {transaction.customerName} —{" "}
              {new Date(transaction.completedAt).toLocaleString()}
              {transaction.referenceCode ? ` — ${transaction.referenceCode}` : ""}
            </option>
          ))}
        </select>
        {error ? (
          <span className={styles.fieldError} id="transaction-select-error">
            {error}
          </span>
        ) : null}
      </label>
      {visibleState.page.totalPages > 1 ? (
        <nav className={styles.pagination} aria-label="Eligible transactions pages">
          <button
            className={styles.secondaryButton}
            type="button"
            disabled={!visibleState.page.hasPrevious}
            onClick={() => setPageNumber((current) => Math.max(0, current - 1))}
          >
            Previous transactions
          </button>
          <span>
            Page {visibleState.page.pageNumber + 1} of {visibleState.page.totalPages}
          </span>
          <button
            className={styles.secondaryButton}
            type="button"
            disabled={!visibleState.page.hasNext}
            onClick={() => setPageNumber((current) => current + 1)}
          >
            More transactions
          </button>
        </nav>
      ) : null}
    </>
  );
}

function RequestForm() {
  const [transactionId, setTransactionId] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [transactionError, setTransactionError] = useState<string | undefined>();
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [created, setCreated] = useState<FeedbackRequest | null>(null);
  const submissionInFlight = useRef(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submissionInFlight.current || created) return;
    if (!transactionId) {
      setTransactionError("Select a completed transaction.");
      return;
    }
    setTransactionError(undefined);
    setError(null);
    submissionInFlight.current = true;
    setIsSubmitting(true);
    try {
      const request = await createFeedbackRequest({
        transactionId,
        ...(scheduledAt ? { scheduledAt: new Date(scheduledAt).toISOString() } : {}),
      });
      setCreated(request);
    } catch (cause) {
      setError(
        cause instanceof ApiError
          ? cause.message
          : "The feedback request could not be created.",
      );
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
  }

  if (created) {
    return (
      <main className={styles.page}>
        <div className={styles.content}>
          <section className={styles.card}>
            <h1 className={styles.title}>Feedback request created</h1>
            <p className={styles.success} role="status">
              Request status: {created.status}. Delivery is handled asynchronously
              by the configured backend integration.
            </p>
            {created.feedbackLink ? (
              <p>
                Public feedback link:{" "}
                <a className={styles.link} href={created.feedbackLink}>
                  {created.feedbackLink}
                </a>
              </p>
            ) : (
              <p className={styles.notice}>
                The backend did not include a public feedback link in its creation response.
              </p>
            )}
            <p className={styles.muted}>
              Keep this link private to the intended customer. It is not shown
              again by request list/detail responses.
            </p>
            <div className={styles.actions}>
              <Link className={styles.button} href="/feedback/requests">
                View feedback requests
              </Link>
              <Link className={styles.secondaryButton} href="/feedback/requests/new">
                Create another
              </Link>
            </div>
          </section>
        </div>
      </main>
    );
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Feedback requests</p>
            <h1 className={styles.title}>Create feedback request</h1>
            <p className={styles.description}>
              Select an existing completed transaction. A transaction can have
              only one feedback request.
            </p>
          </div>
          <Link className={styles.link} href="/feedback/requests">Cancel</Link>
        </header>
        <section className={styles.card}>
          {error ? <p className={styles.error} role="alert">{error}</p> : null}
          <form className={styles.stack} noValidate onSubmit={submit}>
            <TransactionPicker
              value={transactionId}
              error={transactionError}
              onChange={(value) => {
                setTransactionId(value);
                setTransactionError(undefined);
              }}
            />
            <label className={styles.field}>
              <span className={styles.label}>Schedule time (optional)</span>
              <input
                className={styles.input}
                type="datetime-local"
                value={scheduledAt}
                onChange={(event) => setScheduledAt(event.target.value)}
              />
              <span className={styles.muted}>
                Leave blank to enqueue delivery immediately.
              </span>
            </label>
            <button className={styles.button} type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Creating request…" : "Create feedback request"}
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}

export function FeedbackRequestFormPage() {
  return (
    <BusinessRouteGate>
      <RequestForm />
    </BusinessRouteGate>
  );
}
