"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import { listFeedbackRequests } from "../services/feedback-owner.service";
import type {
  FeedbackRequestPage,
  FeedbackRequestStatus,
} from "../types/feedback.types";
import styles from "./feedback.module.scss";

type RequestState =
  | { key: string; status: "loading" }
  | { key: string; status: "ready"; page: FeedbackRequestPage }
  | { key: string; status: "error"; message: string };

const STATUSES: FeedbackRequestStatus[] = [
  "PENDING",
  "SCHEDULED",
  "SENT",
  "DELIVERED",
  "FAILED",
  "COMPLETED",
  "EXPIRED",
  "CANCELLED",
];

function RequestsContent() {
  const [statusFilter, setStatusFilter] = useState<"" | FeedbackRequestStatus>("");
  const [pageNumber, setPageNumber] = useState(0);
  const [retry, setRetry] = useState(0);
  const key = `${statusFilter}:${pageNumber}:${retry}`;
  const [state, setState] = useState<RequestState>({ key: "", status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void listFeedbackRequests({
      status: statusFilter || undefined,
      page: pageNumber,
      signal: controller.signal,
    })
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
                : "Feedback requests could not be loaded.",
          });
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [key, pageNumber, statusFilter]);

  const visibleState =
    state.key === key ? state : { key, status: "loading" as const };

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Feedback management</p>
            <h1 className={styles.title}>Feedback requests</h1>
            <p className={styles.description}>
              Monitor the delivery and completion states returned by the backend.
            </p>
          </div>
          <Link className={styles.button} href="/feedback/requests/new">
            Create request
          </Link>
        </header>
        <section className={styles.card} aria-label="Feedback requests">
          <label className={styles.field}>
            <span className={styles.label}>Filter by status</span>
            <select
              className={styles.select}
              value={statusFilter}
              onChange={(event) => {
                setStatusFilter(event.target.value as "" | FeedbackRequestStatus);
                setPageNumber(0);
              }}
            >
              <option value="">All statuses</option>
              {STATUSES.map((status) => (
                <option key={status} value={status}>{status}</option>
              ))}
            </select>
          </label>
          {visibleState.status === "loading" ? (
            <p role="status">Loading feedback requests…</p>
          ) : visibleState.status === "error" ? (
            <div>
              <p className={styles.error} role="alert">{visibleState.message}</p>
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => setRetry((value) => value + 1)}
              >
                Retry
              </button>
            </div>
          ) : visibleState.page.content.length === 0 ? (
            <p className={styles.notice} role="status">No feedback requests found.</p>
          ) : (
            <>
              <ul className={styles.list}>
                {visibleState.page.content.map((request) => (
                  <li className={styles.row} key={request.id}>
                    <div className={styles.stack}>
                      <p className={styles.rowTitle}>
                        Feedback request for transaction
                      </p>
                      <div className={styles.meta}>
                        <span className={styles.badge}>{request.status}</span>
                        <span>
                          Scheduled{" "}
                          <time dateTime={request.scheduledAt}>
                            {new Date(request.scheduledAt).toLocaleString()}
                          </time>
                        </span>
                        <span>
                          Expires{" "}
                          <time dateTime={request.expiresAt}>
                            {new Date(request.expiresAt).toLocaleDateString()}
                          </time>
                        </span>
                      </div>
                    </div>
                    <Link
                      className={styles.link}
                      href={`/feedback/requests/${encodeURIComponent(request.id)}`}
                    >
                      View details
                    </Link>
                    {request.feedbackId ? (
                      <Link
                        className={styles.link}
                        href={`/feedback/analysis/${encodeURIComponent(request.feedbackId)}`}
                      >
                        View analysis
                      </Link>
                    ) : request.status === "COMPLETED" ? (
                      <span className={styles.muted}>Analysis not available yet</span>
                    ) : null}
                  </li>
                ))}
              </ul>
              {visibleState.page.totalPages > 1 ? (
                <nav className={styles.pagination} aria-label="Feedback request pages">
                  <button
                    className={styles.secondaryButton}
                    type="button"
                    disabled={!visibleState.page.hasPrevious}
                    onClick={() => setPageNumber((value) => Math.max(0, value - 1))}
                  >
                    Previous
                  </button>
                  <span>
                    Page {visibleState.page.pageNumber + 1} of {visibleState.page.totalPages}
                  </span>
                  <button
                    className={styles.secondaryButton}
                    type="button"
                    disabled={!visibleState.page.hasNext}
                    onClick={() => setPageNumber((value) => value + 1)}
                  >
                    Next
                  </button>
                </nav>
              ) : null}
            </>
          )}
        </section>
      </div>
    </main>
  );
}

export function FeedbackRequestsPage() {
  return (
    <BusinessRouteGate>
      <RequestsContent />
    </BusinessRouteGate>
  );
}
