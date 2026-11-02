"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import { createCustomer, getCustomer, updateCustomer } from "../services/customer.service";
import { validateCustomer } from "../schemas/customer.schemas";
import type {
  Customer,
  CustomerFieldErrors,
  CustomerInput,
} from "../types/customer.types";
import styles from "./customers.module.scss";

function serverFieldErrors(error: ApiError): CustomerFieldErrors {
  if (error.status !== 400) {
    return {};
  }
  if (/field: name\./i.test(error.message) || /Customer name must/i.test(error.message)) {
    return { name: "Enter a valid customer name." };
  }
  if (/field: phone\./i.test(error.message) || /valid phone number/i.test(error.message)) {
    return { phone: "Enter a valid phone number." };
  }
  return {};
}

function getErrorMessage(cause: unknown): string {
  if (!(cause instanceof ApiError)) {
    return "The customer could not be saved.";
  }
  if (cause.status === 401) {
    return "Your session has expired. Sign in again to continue.";
  }
  if (cause.status === 403) {
    return "You do not have permission to manage this customer.";
  }
  if (cause.status === 404) {
    return "This customer could not be found.";
  }
  return cause.message;
}

function CustomerForm({
  customerId,
  mode,
}: {
  customerId?: string;
  mode: "create" | "edit";
}) {
  const router = useRouter();
  const [values, setValues] = useState<CustomerInput>({ name: "", phone: "" });
  const [loadState, setLoadState] = useState<
    | { customerId: string; attempt: number; status: "ready"; customer: Customer }
    | { customerId: string; attempt: number; status: "error"; message: string }
  >();
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [fieldErrors, setFieldErrors] = useState<CustomerFieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submissionInFlight = useRef(false);

  useEffect(() => {
    if (mode !== "edit" || !customerId) {
      return;
    }
    const controller = new AbortController();
    let active = true;
    void getCustomer(customerId, controller.signal)
      .then((result) => {
        if (active) {
          setLoadState({
            customerId,
            attempt: loadAttempt,
            status: "ready",
            customer: result,
          });
          setValues({ name: result.name, phone: result.phone });
        }
      })
      .catch((cause: unknown) => {
        if (active && !controller.signal.aborted) {
          setLoadState({
            customerId,
            attempt: loadAttempt,
            status: "error",
            message: getErrorMessage(cause),
          });
        }
      })
    return () => {
      active = false;
      controller.abort();
    };
  }, [customerId, loadAttempt, mode]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submissionInFlight.current) {
      return;
    }
    const input = {
      name: values.name.trim(),
      phone: values.phone.trim(),
    };
    const validationErrors = validateCustomer(input);
    setFieldErrors(validationErrors);
    setError(null);
    if (Object.keys(validationErrors).length > 0) {
      return;
    }

    submissionInFlight.current = true;
    setIsSubmitting(true);
    try {
      const saved =
        mode === "create"
          ? await createCustomer(input)
          : customerId
            ? await updateCustomer(customerId, input)
            : null;
      if (!saved) {
        setError("A customer identifier is required to save changes.");
        return;
      }
      router.replace(
        `/customers/${encodeURIComponent(saved.id)}?${mode === "create" ? "created=1" : "updated=1"}`,
      );
    } catch (cause) {
      if (cause instanceof ApiError) {
        const backendErrors = serverFieldErrors(cause);
        setFieldErrors(backendErrors);
        if (Object.keys(backendErrors).length > 0) {
          setError(null);
        } else {
          setError(getErrorMessage(cause));
        }
      } else {
        setError(getErrorMessage(cause));
      }
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
  }

  if (
    mode === "edit" &&
    !customerId
  ) {
    return <p className={styles.error} role="alert">A customer identifier is required.</p>;
  }
  if (
    mode === "edit" &&
    (!loadState ||
      loadState.customerId !== customerId ||
      loadState.attempt !== loadAttempt)
  ) {
    return <p className={styles.card} role="status">Loading customer details…</p>;
  }
  if (
    mode === "edit" &&
    loadState &&
    loadState.customerId === customerId &&
    loadState.attempt === loadAttempt &&
    loadState.status === "error"
  ) {
    return (
      <section className={styles.card}>
        <p className={styles.error} role="alert">{loadState.message}</p>
        <button
          className={styles.secondaryButton}
          type="button"
          onClick={() => setLoadAttempt((value) => value + 1)}
        >
          Retry loading customer
        </button>
        {customerId ? (
          <Link className={styles.link} href={`/customers/${encodeURIComponent(customerId)}`}>
            Return to customers
          </Link>
        ) : null}
      </section>
    );
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Customer management</p>
            <h1 className={styles.title}>
              {mode === "create" ? "Add customer" : "Edit customer"}
            </h1>
          </div>
          <Link
            className={styles.link}
            href={
              mode === "edit" && customerId
                ? `/customers/${encodeURIComponent(customerId)}`
                : "/customers"
            }
          >
            Cancel
          </Link>
        </header>
        <section className={styles.card}>
          {mode === "edit" ? (
            <p className={styles.muted}>Update the customer’s name or phone number.</p>
          ) : (
            <p className={styles.muted}>
              Phone numbers are validated and normalized by the business service.
            </p>
          )}
          {error ? <p className={styles.error} role="alert">{error}</p> : null}
          <form className={styles.form} noValidate onSubmit={handleSubmit}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="customer-name">
                Customer name *
              </label>
              <input
                className={styles.input}
                id="customer-name"
                name="name"
                value={values.name}
                required
                maxLength={120}
                aria-invalid={Boolean(fieldErrors.name)}
                aria-describedby={fieldErrors.name ? "customer-name-error" : undefined}
                onChange={(event) => setValues((current) => ({ ...current, name: event.target.value }))}
              />
              {fieldErrors.name ? (
                <span className={styles.fieldError} id="customer-name-error">
                  {fieldErrors.name}
                </span>
              ) : null}
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="customer-phone">
                Phone number *
              </label>
              <input
                className={styles.input}
                id="customer-phone"
                name="phone"
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                value={values.phone}
                required
                maxLength={64}
                aria-invalid={Boolean(fieldErrors.phone)}
                aria-describedby={fieldErrors.phone ? "customer-phone-error" : "phone-help"}
                onChange={(event) => setValues((current) => ({ ...current, phone: event.target.value }))}
              />
              {fieldErrors.phone ? (
                <span className={styles.fieldError} id="customer-phone-error">
                  {fieldErrors.phone}
                </span>
              ) : (
                <span className={styles.muted} id="phone-help">
                  International numbers are accepted; national-format support depends on the backend’s configured region.
                </span>
              )}
            </div>
            <button className={styles.button} type="submit" disabled={isSubmitting}>
              {isSubmitting
                ? "Saving customer…"
                : mode === "create"
                  ? "Create customer"
                  : "Save changes"}
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}

export function CustomerFormPage({
  customerId,
  mode,
}: {
  customerId?: string;
  mode: "create" | "edit";
}) {
  return (
    <BusinessRouteGate>
      <CustomerForm customerId={customerId} mode={mode} />
    </BusinessRouteGate>
  );
}
