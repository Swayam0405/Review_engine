"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import {
  cancelFeedbackRequest,
  getFeedbackRequest,
  retryFeedbackRequest,
} from "../services/feedback-owner.service";
import type { FeedbackRequest } from "../types/feedback.types";
import styles from "./feedback.module.scss";

type DetailState =
  | { status: "loading" }
  | { status: "ready"; request: FeedbackRequest }
  | { status: "error"; message: string };

function RequestDetails({ requestId }: { requestId: string }) {
  const [revision, setRevision] = useState(0);
  const [loadResult, setLoadResult] = useState<{
    revision: number;
    result: DetailState;
  } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionPending, setActionPending] = useState(false);
  const actionInFlight = useRef(false);

  const load = useCallback((signal?: AbortSignal) => {
    return getFeedbackRequest(requestId, signal);
  }, [requestId]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void load(controller.signal)
      .then((request) => {
        if (active) {
          setLoadResult({ revision, result: { status: "ready", request } });
        }
      })
      .catch((cause: unknown) => {
        if (active && !controller.signal.aborted) {
          setLoadResult({
            revision,
            result: {
              status: "error",
              message:
                cause instanceof ApiError
                  ? cause.message
                  : "Feedback request details could not be loaded.",
            },
          });
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [load, revision]);

  const state =
    loadResult?.revision === revision
      ? loadResult.result
      : ({ status: "loading" } as const);
  async function performAction(action: "cancel" | "retry") {
    if (actionInFlight.current) return;
    actionInFlight.current = true;
    setActionPending(true);
    setActionError(null);
    try {
      if (action === "cancel") {
        await cancelFeedbackRequest(requestId);
      } else {
        await retryFeedbackRequest(requestId);
      }
      setRevision((value) => value + 1);
    } catch (cause) {
      setActionError(
        cause instanceof Error
          ? cause.message
          : "The feedback request could not be updated.",
      );
    } finally {
      actionInFlight.current = false;
      setActionPending(false);
    }
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Feedback requests</p>
            <h1 className={styles.title}>Request details</h1>
          </div>
          <Link className={styles.link} href="/feedback/requests">Back to requests</Link>
        </header>
        {state.status === "loading" ? (
          <section className={styles.card} role="status">Loading request details…</section>
        ) : state.status === "error" ? (
          <section className={styles.card}>
            <p className={styles.error} role="alert">{state.message}</p>
            <button
              className={styles.secondaryButton}
              type="button"
              onClick={() => setRevision((value) => value + 1)}
            >
              Retry
            </button>
          </section>
        ) : (
          <section className={styles.card}>
            <span className={styles.badge}>{state.request.status}</span>
            <dl className={styles.details}>
              <div>
                <dt>Transaction</dt>
                <dd>{state.request.transactionId}</dd>
              </div>
              <div>
                <dt>Scheduled</dt>
                <dd><time dateTime={state.request.scheduledAt}>{new Date(state.request.scheduledAt).toLocaleString()}</time></dd>
              </div>
              <div>
                <dt>Created</dt>
                <dd><time dateTime={state.request.createdAt}>{new Date(state.request.createdAt).toLocaleString()}</time></dd>
              </div>
              <div>
                <dt>Expires</dt>
                <dd><time dateTime={state.request.expiresAt}>{new Date(state.request.expiresAt).toLocaleString()}</time></dd>
              </div>
              {state.request.sentAt ? (
                <div>
                  <dt>Sent</dt>
                  <dd><time dateTime={state.request.sentAt}>{new Date(state.request.sentAt).toLocaleString()}</time></dd>
                </div>
              ) : null}
              {state.request.deliveredAt ? (
                <div>
                  <dt>Delivered</dt>
                  <dd><time dateTime={state.request.deliveredAt}>{new Date(state.request.deliveredAt).toLocaleString()}</time></dd>
                </div>
              ) : null}
              {state.request.completedAt ? (
                <div>
                  <dt>Completed</dt>
                  <dd><time dateTime={state.request.completedAt}>{new Date(state.request.completedAt).toLocaleString()}</time></dd>
                </div>
              ) : null}
            </dl>
            {actionError ? <p className={styles.error} role="alert">{actionError}</p> : null}
            <div className={styles.actions}>
              {state.request.feedbackId ? (
                <Link
                  className={styles.link}
                  href={`/feedback/analysis/${encodeURIComponent(state.request.feedbackId)}`}
                >
                  View feedback analysis
                </Link>
              ) : state.request.status === "COMPLETED" ? (
                <p className={styles.muted}>Analysis is not available yet.</p>
              ) : null}
              {state.request.status !== "COMPLETED" &&
              state.request.status !== "CANCELLED" &&
              state.request.status !== "EXPIRED" ? (
                <button
                  className={styles.secondaryButton}
                  type="button"
                  disabled={actionPending}
                  onClick={() => void performAction("cancel")}
                >
                  {actionPending ? "Updating…" : "Cancel request"}
                </button>
              ) : null}
              {state.request.status === "FAILED" ? (
                <button
                  className={styles.button}
                  type="button"
                  disabled={actionPending}
                  onClick={() => void performAction("retry")}
                >
                  {actionPending ? "Queueing…" : "Retry delivery"}
                </button>
              ) : null}
            </div>
            <p className={styles.muted}>
              Existing public feedback links are not returned by the backend on
              detail requests. Only use a link provided by the original creation
              response.
            </p>
          </section>
        )}
      </div>
    </main>
  );
}

export function FeedbackRequestDetailsPage({ requestId }: { requestId: string }) {
  return (
    <BusinessRouteGate>
      <RequestDetails requestId={requestId} />
    </BusinessRouteGate>
  );
}
