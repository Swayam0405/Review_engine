"use client";

import { useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import { createTransaction } from "../services/transaction.service";
import { validateTransaction } from "../schemas/transaction.schemas";
import type {
  TransactionFieldErrors,
} from "../types/transaction.types";
import { CustomerSelector } from "./CustomerSelector";
import styles from "./transactions.module.scss";

function mapCreateError(cause: unknown): {
  message: string;
  fieldErrors: TransactionFieldErrors;
} {
  if (!(cause instanceof ApiError)) {
    return {
      message: "The transaction could not be recorded.",
      fieldErrors: {},
    };
  }
  if (/field: customerId\./i.test(cause.message)) {
    return {
      message: "",
      fieldErrors: { customerId: "Select a valid customer." },
    };
  }
  if (/field: referenceCode\./i.test(cause.message) || /Reference code must/i.test(cause.message)) {
    return {
      message: "",
      fieldErrors: { referenceCode: "Enter a valid reference code." },
    };
  }
  if (
    /field: completedAt\./i.test(cause.message) ||
    /Completion time must/i.test(cause.message)
  ) {
    return {
      message: "",
      fieldErrors: {
        completedAt: "Enter a valid completion time no more than five minutes ahead.",
      },
    };
  }
  if (cause.status === 404) {
    return {
      message: "The selected customer was not found in your business.",
      fieldErrors: {},
    };
  }
  return { message: cause.message, fieldErrors: {} };
}

function TransactionForm() {
  const router = useRouter();
  const [customerId, setCustomerId] = useState("");
  const [referenceCode, setReferenceCode] = useState("");
  const [completedAt, setCompletedAt] = useState("");
  const [fieldErrors, setFieldErrors] = useState<TransactionFieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submissionInFlight = useRef(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submissionInFlight.current) {
      return;
    }
    const input = {
      customerId,
      ...(referenceCode.trim()
        ? { referenceCode: referenceCode.trim() }
        : {}),
      ...(completedAt ? { completedAt } : {}),
    };
    const errors = validateTransaction(input);
    setFieldErrors(errors);
    setError(null);
    if (Object.keys(errors).length > 0) {
      return;
    }

    submissionInFlight.current = true;
    setIsSubmitting(true);
    try {
      const request = {
        ...input,
        ...(completedAt
          ? { completedAt: localDateTimeToOffset(completedAt) }
          : {}),
      };
      const transaction = await createTransaction(request);
      router.replace(
        `/transactions/${encodeURIComponent(transaction.id)}?created=1`,
      );
    } catch (cause) {
      const mapped = mapCreateError(cause);
      setFieldErrors(mapped.fieldErrors);
      setError(mapped.message || null);
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Transaction management</p>
            <h1 className={styles.title}>Add transaction</h1>
            <p className={styles.description}>
              Record a completed transaction. The backend sets its status.
            </p>
          </div>
          <Link className={styles.link} href="/transactions">Cancel</Link>
        </header>
        <section className={styles.card}>
          {error ? <p className={styles.error} role="alert">{error}</p> : null}
          <form className={styles.form} noValidate onSubmit={handleSubmit}>
            <CustomerSelector
              value={customerId}
              error={fieldErrors.customerId}
              onChange={(value) => {
                setCustomerId(value);
                setFieldErrors((current) => ({ ...current, customerId: undefined }));
              }}
            />
            <div className={styles.field}>
              <label className={styles.label} htmlFor="transaction-completed-at">
                Completion date and time (optional)
              </label>
              <input
                className={styles.input}
                id="transaction-completed-at"
                name="completedAt"
                type="datetime-local"
                value={completedAt}
                aria-invalid={Boolean(fieldErrors.completedAt)}
                aria-describedby={
                  fieldErrors.completedAt
                    ? "transaction-completed-at-error"
                    : "transaction-completed-at-help"
                }
                onChange={(event) => setCompletedAt(event.target.value)}
              />
              {fieldErrors.completedAt ? (
                <span
                  className={styles.fieldError}
                  id="transaction-completed-at-error"
                >
                  {fieldErrors.completedAt}
                </span>
              ) : (
                <span className={styles.muted} id="transaction-completed-at-help">
                  Leave blank to use the current server time.
                </span>
              )}
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="transaction-reference">
                Reference code (optional)
              </label>
              <input
                className={styles.input}
                id="transaction-reference"
                name="referenceCode"
                maxLength={100}
                value={referenceCode}
                aria-invalid={Boolean(fieldErrors.referenceCode)}
                aria-describedby={
                  fieldErrors.referenceCode
                    ? "transaction-reference-error"
                    : "transaction-reference-help"
                }
                onChange={(event) => setReferenceCode(event.target.value)}
              />
              {fieldErrors.referenceCode ? (
                <span
                  className={styles.fieldError}
                  id="transaction-reference-error"
                >
                  {fieldErrors.referenceCode}
                </span>
              ) : (
                <span className={styles.muted} id="transaction-reference-help">
                  Must be unique for your business when provided.
                </span>
              )}
            </div>
            <button
              className={styles.button}
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Recording transaction…" : "Record completed transaction"}
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}

export function TransactionFormPage() {
  return (
    <BusinessRouteGate>
      <TransactionForm />
    </BusinessRouteGate>
  );
}

function localDateTimeToOffset(value: string): string {
  return new Date(value).toISOString();
}
