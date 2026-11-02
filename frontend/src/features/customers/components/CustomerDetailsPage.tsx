"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import { getCustomer } from "../services/customer.service";
import type { Customer } from "../types/customer.types";
import styles from "./customers.module.scss";

type DetailState =
  | { status: "loading"; key: string }
  | { status: "ready"; key: string; customer: Customer }
  | { status: "error"; key: string; message: string; notFound: boolean };

function DetailsContent({
  customerId,
  notice,
}: {
  customerId: string;
  notice?: string;
}) {
  const [state, setState] = useState<DetailState>({ status: "loading", key: "" });
  const [retry, setRetry] = useState(0);
  const requestKey = `${customerId}:${retry}`;

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void getCustomer(customerId, controller.signal)
      .then((customer) => {
        if (active) {
          setState({ status: "ready", key: requestKey, customer });
        }
      })
      .catch((cause: unknown) => {
        if (!active || controller.signal.aborted) {
          return;
        }
        const error = cause instanceof ApiError ? cause : null;
        setState({
          status: "error",
          key: requestKey,
          notFound: error?.status === 404,
          message:
            error?.status === 401
              ? "Your session has expired. Sign in again to continue."
              : error?.status === 403
                ? "You do not have permission to view this customer."
                : error?.message ?? "Customer details could not be loaded.",
        });
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [customerId, retry, requestKey]);

  const visibleState =
    state.key === requestKey
      ? state
      : { status: "loading" as const, key: requestKey };

  if (visibleState.status === "loading") {
    return <p className={styles.card} role="status">Loading customer details…</p>;
  }
  if (visibleState.status === "error") {
    return (
      <main className={styles.page}>
        <div className={styles.content}>
          <section className={styles.stateCard}>
            <h1 className={styles.title}>
              {visibleState.notFound ? "Customer not found" : "Customer details unavailable"}
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
            <p><Link className={styles.link} href="/customers">Back to customers</Link></p>
          </section>
        </div>
      </main>
    );
  }

  const { customer } = visibleState;
  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Customer details</p>
            <h1 className={styles.title}>{customer.name}</h1>
          </div>
          <div className={styles.actions}>
            <Link
              className={styles.button}
              href={`/customers/${encodeURIComponent(customer.id)}/edit`}
            >
              Edit customer
            </Link>
            <Link className={styles.secondaryButton} href="/customers">
              All customers
            </Link>
          </div>
        </header>
        <section className={styles.card}>
          {notice ? <p className={styles.success} role="status">{notice}</p> : null}
          <dl className={styles.details}>
            <div>
              <dt>Name</dt>
              <dd>{customer.name}</dd>
            </div>
            <div>
              <dt>Phone number</dt>
              <dd>{customer.phone}</dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd><time dateTime={customer.createdAt}>{new Date(customer.createdAt).toLocaleString()}</time></dd>
            </div>
            <div>
              <dt>Last updated</dt>
              <dd><time dateTime={customer.updatedAt}>{new Date(customer.updatedAt).toLocaleString()}</time></dd>
            </div>
          </dl>
        </section>
      </div>
    </main>
  );
}

export function CustomerDetailsPage({
  customerId,
  notice,
}: {
  customerId: string;
  notice?: string;
}) {
  return (
    <BusinessRouteGate>
      <DetailsContent customerId={customerId} notice={notice} />
    </BusinessRouteGate>
  );
}
