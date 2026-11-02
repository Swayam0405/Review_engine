"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import type {
  CustomerPage as CustomerPageData,
} from "../types/customer.types";
import { listCustomers } from "../services/customer.service";
import styles from "./customers.module.scss";

type ListState =
  | { status: "loading"; key: string }
  | { status: "ready"; key: string; page: CustomerPageData }
  | { status: "error"; key: string; message: string };

function ListContent() {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [pageNumber, setPageNumber] = useState(0);
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState<ListState>({ status: "loading", key: "" });
  const requestKey = JSON.stringify([debouncedQuery, pageNumber, retry]);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPageNumber(0);
    }, 250);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void listCustomers({
      name: debouncedQuery,
      page: pageNumber,
      signal: controller.signal,
    })
      .then((page) => {
        if (active) {
          setState({ status: "ready", key: requestKey, page });
        }
      })
      .catch((cause: unknown) => {
        if (!active || controller.signal.aborted) {
          return;
        }
        setState({
          status: "error",
          key: requestKey,
          message:
            cause instanceof ApiError
              ? cause.message
              : "Customers could not be loaded.",
        });
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [debouncedQuery, pageNumber, retry, requestKey]);

  const visibleState =
    state.key === requestKey ? state : { status: "loading" as const, key: requestKey };

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Customer management</p>
            <h1 className={styles.title}>Customers</h1>
            <p className={styles.description}>
              View and manage customers associated with your business.
            </p>
          </div>
          <Link className={styles.button} href="/customers/new">
            Add customer
          </Link>
        </header>

        <section className={styles.card} aria-label="Customer list">
          <div className={styles.toolbar}>
            <label className={styles.field}>
              <span className={styles.label}>Search customers by name</span>
              <input
                className={styles.input}
                type="search"
                value={query}
                maxLength={120}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search by name"
              />
            </label>
          </div>

          {visibleState.status === "loading" ? (
            <div role="status" aria-label="Loading customers">
              <div className={styles.skeleton} />
              <div className={styles.skeleton} />
              <div className={styles.skeleton} />
            </div>
          ) : visibleState.status === "error" ? (
            <div>
              <p className={styles.error} role="alert">
                {visibleState.message}
              </p>
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => setRetry((value) => value + 1)}
              >
                Retry
              </button>
            </div>
          ) : visibleState.page.content.length === 0 ? (
            debouncedQuery ? (
              <div className={styles.notice} role="status">
                No customers match “{debouncedQuery}”.
              </div>
            ) : (
              <div className={styles.notice} role="status">
                <p>No customers yet.</p>
                <Link className={styles.link} href="/customers/new">
                  Add your first customer
                </Link>
              </div>
            )
          ) : (
            <>
              <ul className={styles.list}>
                {visibleState.page.content.map((customer) => (
                  <li className={styles.customerRow} key={customer.id}>
                    <div>
                      <p className={styles.customerName}>{customer.name}</p>
                      <p className={styles.muted}>
                        Updated{" "}
                        <time dateTime={customer.updatedAt}>
                          {new Date(customer.updatedAt).toLocaleDateString()}
                        </time>
                      </p>
                    </div>
                    <Link
                      className={styles.link}
                      href={`/customers/${encodeURIComponent(customer.id)}`}
                      aria-label={`View ${customer.name}`}
                    >
                      View details
                    </Link>
                  </li>
                ))}
              </ul>
              {visibleState.page.totalPages > 1 ? (
                <nav className={styles.pagination} aria-label="Customer pages">
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

export function CustomerListPage() {
  return (
    <BusinessRouteGate>
      <ListContent />
    </BusinessRouteGate>
  );
}
