"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import { getTransaction } from "../services/transaction.service";
import type { Transaction } from "../types/transaction.types";
import styles from "./transactions.module.scss";

type DetailState =
  | { key: string; status: "ready"; transaction: Transaction }
  | { key: string; status: "error"; message: string; notFound: boolean };

function DetailContent({
  transactionId,
  created,
}: {
  transactionId: string;
  created: boolean;
}) {
  const [retry, setRetry] = useState(0);
  const key = `${transactionId}:${retry}`;
  const [state, setState] = useState<DetailState>();

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void getTransaction(transactionId, controller.signal)
      .then((transaction) => {
        if (active) {
          setState({ key, status: "ready", transaction });
        }
      })
      .catch((cause: unknown) => {
        if (!active || controller.signal.aborted) {
          return;
        }
        const apiError = cause instanceof ApiError ? cause : null;
        setState({
          key,
          status: "error",
          notFound: apiError?.status === 404,
          message:
            apiError?.status === 401
              ? "Your session has expired. Sign in again to continue."
              : apiError?.status === 403
                ? "You do not have permission to view this transaction."
                : apiError?.message ?? "Transaction details could not be loaded.",
        });
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [key, transactionId]);

  const visibleState =
    state?.key === key ? state : { status: "loading" as const };
  if (visibleState.status === "loading") {
    return <p className={styles.card} role="status">Loading transaction…</p>;
  }
  if (visibleState.status === "error") {
    return (
      <main className={styles.page}>
        <div className={styles.content}>
          <section className={styles.stateCard}>
            <h1 className={styles.title}>
              {visibleState.notFound ? "Transaction not found" : "Transaction unavailable"}
            </h1>
            <p className={styles.error} role="alert">{visibleState.message}</p>
            {!visibleState.notFound ? (
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => setRetry((value) => value + 1)}
              >
                Retry
              </button>
            ) : null}
            <p><Link className={styles.link} href="/transactions">Back to transactions</Link></p>
          </section>
        </div>
      </main>
    );
  }

  const { transaction } = visibleState;
  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Transaction details</p>
            <h1 className={styles.title}>Completed transaction</h1>
          </div>
          <div className={styles.actions}>
            <Link className={styles.secondaryButton} href="/transactions">
              All transactions
            </Link>
          </div>
        </header>
        <section className={styles.card}>
          {created ? (
            <p className={styles.success} role="status">
              Transaction recorded successfully.
            </p>
          ) : null}
          <dl className={styles.details}>
            <div>
              <dt>Customer</dt>
              <dd>
                <Link
                  className={styles.link}
                  href={`/customers/${encodeURIComponent(transaction.customerId)}`}
                >
                  {transaction.customerName}
                </Link>
              </dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd><span className={styles.status}>{transaction.status}</span></dd>
            </div>
            <div>
              <dt>Reference code</dt>
              <dd>{transaction.referenceCode ?? "—"}</dd>
            </div>
            <div>
              <dt>Completed</dt>
              <dd>
                <time dateTime={transaction.completedAt}>
                  {new Date(transaction.completedAt).toLocaleString()}
                </time>
              </dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd>
                <time dateTime={transaction.createdAt}>
                  {new Date(transaction.createdAt).toLocaleString()}
                </time>
              </dd>
            </div>
          </dl>
        </section>
      </div>
    </main>
  );
}

export function TransactionDetailsPage({
  transactionId,
  created,
}: {
  transactionId: string;
  created: boolean;
}) {
  return (
    <BusinessRouteGate>
      <DetailContent transactionId={transactionId} created={created} />
    </BusinessRouteGate>
  );
}
