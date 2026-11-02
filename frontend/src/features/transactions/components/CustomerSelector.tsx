"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { listCustomers } from "../../customers/services/customer.service";
import type {
  CustomerPage,
  CustomerSummary,
} from "../../customers/types/customer.types";
import styles from "./transactions.module.scss";

type CustomerLoadState =
  | { status: "loading"; key: string }
  | { status: "ready"; key: string; page: CustomerPage }
  | { status: "error"; key: string; message: string };

export function CustomerSelector({
  value,
  error,
  onChange,
}: {
  value: string;
  error?: string;
  onChange: (customerId: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [pageNumber, setPageNumber] = useState(0);
  const [retry, setRetry] = useState(0);
  const [selectedCustomer, setSelectedCustomer] =
    useState<CustomerSummary | null>(null);
  const key = JSON.stringify([debouncedQuery, pageNumber, retry]);
  const [state, setState] = useState<CustomerLoadState>({
    status: "loading",
    key: "",
  });

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
                : "Customers could not be loaded.",
          });
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [debouncedQuery, key, pageNumber]);

  const visibleState =
    state.key === key ? state : { status: "loading" as const, key };

  return (
    <div className={styles.form}>
      <label className={styles.field} htmlFor="customer-search">
        <span className={styles.label}>Find a customer</span>
        <input
          className={styles.input}
          id="customer-search"
          type="search"
          maxLength={120}
          value={query}
          placeholder="Search by name"
          onChange={(event) => setQuery(event.target.value)}
        />
      </label>

      {visibleState.status === "loading" ? (
        <p role="status">Loading customers…</p>
      ) : visibleState.status === "error" ? (
        <div>
          <p className={styles.error} role="alert">{visibleState.message}</p>
          <button
            className={styles.secondaryButton}
            type="button"
            onClick={() => setRetry((current) => current + 1)}
          >
            Retry customers
          </button>
        </div>
      ) : visibleState.page.content.length === 0 ? (
        <div className={styles.notice} role="status">
          <p>
            {debouncedQuery
              ? "No customers match this search."
              : "No customers yet. Add a customer before recording a transaction."}
          </p>
          {!debouncedQuery ? (
            <Link className={styles.link} href="/customers/new">
              Add customer
            </Link>
          ) : null}
        </div>
      ) : (
        <>
          <label className={styles.field}>
            <span className={styles.label}>Customer *</span>
            <select
              className={styles.select}
              value={value}
              required
              aria-invalid={Boolean(error)}
              aria-describedby={error ? "transaction-customer-error" : undefined}
              onChange={(event) => {
                const nextCustomer =
                  visibleState.page.content.find(
                    (customer) => customer.id === event.target.value,
                  ) ?? null;
                setSelectedCustomer(nextCustomer);
                onChange(event.target.value);
              }}
            >
              <option value="">Select a customer</option>
              {selectedCustomer &&
              selectedCustomer.id === value &&
              !visibleState.page.content.some(
                (customer) => customer.id === selectedCustomer.id,
              ) ? (
                <option value={selectedCustomer.id}>
                  {selectedCustomer.name}
                </option>
              ) : null}
              {visibleState.page.content.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </select>
          </label>
          {visibleState.page.totalPages > 1 ? (
            <nav className={styles.pagination} aria-label="Customer selection pages">
              <button
                className={styles.secondaryButton}
                type="button"
                disabled={!visibleState.page.hasPrevious}
                onClick={() => setPageNumber((current) => Math.max(0, current - 1))}
              >
                Previous customers
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
                More customers
              </button>
            </nav>
          ) : null}
        </>
      )}
      {error ? (
        <span className={styles.fieldError} id="transaction-customer-error">
          {error}
        </span>
      ) : null}
    </div>
  );
}
