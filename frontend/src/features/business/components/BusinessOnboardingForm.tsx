"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "../../auth/hooks/useAuth";
import { getSafeReturnTo } from "../../auth/utils/return-to";
import { useBusiness } from "../providers/BusinessProvider";
import { createBusiness } from "../services/business.service";
import { validateBusiness } from "../schemas/business.schemas";
import type {
  BusinessFieldErrors,
  BusinessInput,
} from "../types/business.types";
import { BusinessField } from "./BusinessField";
import { BusinessRouteGate } from "./BusinessRouteGate";
import styles from "./business.module.scss";

function getOnboardingDestination(): string {
  if (typeof window === "undefined") {
    return "/dashboard";
  }
  const returnTo = getSafeReturnTo(
    new URLSearchParams(window.location.search).get("returnTo"),
  );
  return returnTo && returnTo !== "/business/onboarding"
    ? returnTo
    : "/dashboard";
}

export function BusinessOnboardingForm() {
  const router = useRouter();
  const { refreshCurrentUser } = useAuth();
  const { refreshBusiness } = useBusiness();
  const [values, setValues] = useState<BusinessInput>({
    name: "",
    category: "",
    address: "",
    timezone: "UTC",
    googleReviewUrl: null,
  });
  const [fieldErrors, setFieldErrors] = useState<BusinessFieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [businessCreated, setBusinessCreated] = useState(false);
  const submissionInFlight = useRef(false);

  async function refreshCreatedBusiness() {
    setError(null);
    setIsSubmitting(true);
    try {
      await Promise.all([refreshCurrentUser(), refreshBusiness()]);
      router.replace(getOnboardingDestination());
    } catch {
      setError(
        "Your business was created, but its profile could not be refreshed. Retry the refresh to continue.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  function updateField(key: keyof BusinessInput, value: string) {
    setValues((current) => ({ ...current, [key]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submissionInFlight.current) {
      return;
    }
    const input = {
      ...values,
      name: values.name.trim(),
      category: values.category.trim(),
      address: values.address.trim(),
      timezone: values.timezone.trim(),
      googleReviewUrl: values.googleReviewUrl?.trim() || null,
    };
    const errors = validateBusiness(input);
    setFieldErrors(errors);
    setError(null);
    setSuccess(null);
    if (Object.keys(errors).length > 0) {
      return;
    }

    submissionInFlight.current = true;
    setIsSubmitting(true);
    let created = false;
    try {
      await createBusiness(input);
      created = true;
      setBusinessCreated(true);
      setSuccess("Your business profile has been created.");
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Business onboarding could not be completed.",
      );
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
    if (created) {
      await refreshCreatedBusiness();
    }
  }

  return (
    <BusinessRouteGate onboarding>
      <main className={styles.page}>
        <div className={styles.content}>
          <header className={styles.header}>
            <p className={styles.eyebrow}>Business onboarding</p>
            <h1 className={styles.title}>Set up your business</h1>
            <p className={styles.description}>
              Add your business profile to continue. Ownership is linked to your
              signed-in account.
            </p>
          </header>
          <section className={styles.card}>
            <form className={styles.form} noValidate onSubmit={handleSubmit}>
              {error ? (
                <p className={styles.error} role="alert">
                  {error}
                </p>
              ) : null}
              {success ? (
                <p className={styles.success} role="status">
                  {success}
                </p>
              ) : null}
              <BusinessField
                id="business-name"
                label="Business name"
                value={values.name}
                error={fieldErrors.name}
                onChange={(value) => updateField("name", value)}
              />
              <BusinessField
                id="business-category"
                label="Category"
                value={values.category}
                error={fieldErrors.category}
                onChange={(value) => updateField("category", value)}
              />
              <div className={styles.grid}>
                <BusinessField
                  id="business-timezone"
                  label="Time zone"
                  type="timezone"
                  value={values.timezone}
                  error={fieldErrors.timezone}
                  onChange={(value) => updateField("timezone", value)}
                />
                <BusinessField
                  id="google-review-url"
                  label="Google review URL"
                  type="url"
                  required={false}
                  value={values.googleReviewUrl ?? ""}
                  error={fieldErrors.googleReviewUrl}
                  onChange={(value) => updateField("googleReviewUrl", value)}
                />
              </div>
              <div className={styles.field}>
                <label className={styles.label} htmlFor="business-address">
                  Business address *
                </label>
                <textarea
                  className={`${styles.textarea} ${fieldErrors.address ? styles.invalid : ""}`}
                  id="business-address"
                  name="address"
                  value={values.address}
                  required
                  aria-invalid={Boolean(fieldErrors.address)}
                  aria-describedby={
                    fieldErrors.address ? "business-address-error" : undefined
                  }
                  onChange={(event) => updateField("address", event.target.value)}
                />
                {fieldErrors.address ? (
                  <span
                    className={styles.fieldError}
                    id="business-address-error"
                  >
                    {fieldErrors.address}
                  </span>
                ) : null}
              </div>
              <p className={styles.muted}>
                You can configure a Google review URL later. Do not enter a
                generated or guessed link.
              </p>
              <button
                className={styles.button}
                type="submit"
                disabled={isSubmitting || businessCreated}
              >
                {businessCreated
                  ? "Business created"
                  : isSubmitting
                    ? "Saving business…"
                    : "Create business profile"}
              </button>
              {businessCreated && error ? (
                <button
                  className={styles.link}
                  type="button"
                  disabled={isSubmitting}
                  onClick={() => void refreshCreatedBusiness()}
                >
                  Retry profile refresh
                </button>
              ) : null}
            </form>
          </section>
        </div>
      </main>
    </BusinessRouteGate>
  );
}
