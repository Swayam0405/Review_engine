"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import { listTransactions } from "../services/transaction.service";
import type { TransactionPage } from "../types/transaction.types";
import styles from "./transactions.module.scss";

type ListState =
  | { status: "loading"; key: string }
  | { status: "ready"; key: string; page: TransactionPage }
  | { status: "error"; key: string; message: string };

function ListContent() {
  const [reference, setReference] = useState("");
  const [referenceFilter, setReferenceFilter] = useState("");
  const [pageNumber, setPageNumber] = useState(0);
  const [retry, setRetry] = useState(0);
  const key = JSON.stringify([referenceFilter, pageNumber, retry]);
  const [state, setState] = useState<ListState>({ status: "loading", key: "" });

  useEffect(() => {
    const timer = setTimeout(() => {
      setReferenceFilter(reference.trim());
      setPageNumber(0);
    }, 250);
    return () => clearTimeout(timer);
  }, [reference]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void listTransactions({
      referenceCode: referenceFilter,
      page: pageNumber,
      signal: controller.signal,
    })
      .then((page) => {
        if (active) {
          setState({ status: "ready", key, page });
        }
      })
      .catch((cause: unknown) => {
        if (active && !controller.signal.aborted) {
          setState({
            status: "error",
            key,
            message:
              cause instanceof ApiError
                ? cause.message
                : "Transactions could not be loaded.",
          });
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [key, pageNumber, referenceFilter]);

  const visibleState =
    state.key === key ? state : { status: "loading" as const, key };

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Transaction management</p>
            <h1 className={styles.title}>Transactions</h1>
            <p className={styles.description}>
              View transactions recorded for your business.
            </p>
          </div>
          <Link className={styles.button} href="/transactions/new">
            Add transaction
          </Link>
        </header>

        <section className={styles.card} aria-label="Transaction list">
          <div className={styles.toolbar}>
            <label className={styles.field}>
              <span className={styles.label}>Search by reference code</span>
              <input
                className={styles.input}
                type="search"
                maxLength={100}
                value={reference}
                placeholder="Reference code"
                onChange={(event) => setReference(event.target.value)}
              />
            </label>
          </div>
          {visibleState.status === "loading" ? (
            <div role="status" aria-label="Loading transactions">
              <div className={styles.skeleton} />
              <div className={styles.skeleton} />
            </div>
          ) : visibleState.status === "error" ? (
            <div>
              <p className={styles.error} role="alert">{visibleState.message}</p>
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => setRetry((current) => current + 1)}
              >
                Retry
              </button>
            </div>
          ) : visibleState.page.content.length === 0 ? (
            <p className={styles.notice} role="status">
              {referenceFilter
                ? `No transactions match “${referenceFilter}”.`
                : "No transactions recorded yet."}
            </p>
          ) : (
            <>
              <ul className={styles.list}>
                {visibleState.page.content.map((transaction) => (
                  <li className={styles.row} key={transaction.id}>
                    <div>
                      <p className={styles.rowTitle}>{transaction.customerName}</p>
                      <p className={styles.muted}>
                        Completed{" "}
                        <time dateTime={transaction.completedAt}>
                          {new Date(transaction.completedAt).toLocaleString()}
                        </time>
                      </p>
                    </div>
                    <div>
                      <p>
                        Reference: {transaction.referenceCode ?? "—"}
                      </p>
                      <span className={styles.status}>{transaction.status}</span>
                    </div>
                    <Link
                      className={styles.link}
                      href={`/transactions/${encodeURIComponent(transaction.id)}`}
                      aria-label={`View transaction for ${transaction.customerName}`}
                    >
                      View details
                    </Link>
                  </li>
                ))}
              </ul>
              {visibleState.page.totalPages > 1 ? (
                <nav className={styles.pagination} aria-label="Transaction pages">
                  <button
                    className={styles.secondaryButton}
                    type="button"
                    disabled={!visibleState.page.hasPrevious}
                    onClick={() => setPageNumber((current) => Math.max(0, current - 1))}
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
                    onClick={() => setPageNumber((current) => current + 1)}
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

export function TransactionListPage() {
  return (
    <BusinessRouteGate>
      <ListContent />
    </BusinessRouteGate>
  );
}
