"use client";

import { useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { updateBusiness } from "../services/business.service";
import { validateBusiness } from "../schemas/business.schemas";
import type {
  BusinessFieldErrors,
  BusinessInput,
  BusinessProfile,
} from "../types/business.types";
import { useBusiness } from "../providers/BusinessProvider";
import { BusinessField } from "./BusinessField";
import styles from "./business.module.scss";

export function BusinessProfileForm({
  business,
}: {
  business: BusinessProfile;
}) {
  const { refreshBusiness } = useBusiness();
  const [values, setValues] = useState<BusinessInput>({
    name: business.name,
    category: business.category,
    address: business.address,
    timezone: business.timezone,
    googleReviewUrl: business.googleReviewUrl,
  });
  const [fieldErrors, setFieldErrors] = useState<BusinessFieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submissionInFlight = useRef(false);

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
    try {
      await updateBusiness(input);
      await refreshBusiness();
      setSuccess("Business profile updated.");
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Business profile could not be updated.",
      );
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
  }

  return (
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
          <div className={styles.field}>
            <label className={styles.label} htmlFor="google-review-url">
              Google review URL (optional)
            </label>
            <input
              className={`${styles.input} ${fieldErrors.googleReviewUrl ? styles.invalid : ""}`}
              id="google-review-url"
              type="url"
              value={values.googleReviewUrl ?? ""}
              aria-invalid={Boolean(fieldErrors.googleReviewUrl)}
              aria-describedby={
                fieldErrors.googleReviewUrl
                  ? "google-review-url-error"
                  : undefined
              }
              onChange={(event) =>
                updateField("googleReviewUrl", event.target.value)
              }
            />
            {fieldErrors.googleReviewUrl ? (
              <span
                className={styles.fieldError}
                id="google-review-url-error"
              >
                {fieldErrors.googleReviewUrl}
              </span>
            ) : null}
            <Link className={styles.link} href="/business/google-review">
              Configure review link and QR code
            </Link>
          </div>
        </div>
        <div className={styles.field}>
          <label className={styles.label} htmlFor="business-address">
            Business address
          </label>
          <textarea
            className={`${styles.textarea} ${fieldErrors.address ? styles.invalid : ""}`}
            id="business-address"
            value={values.address}
            aria-invalid={Boolean(fieldErrors.address)}
            aria-describedby={fieldErrors.address ? "business-address-error" : undefined}
            onChange={(event) => updateField("address", event.target.value)}
          />
          {fieldErrors.address ? (
            <span className={styles.fieldError} id="business-address-error">
              {fieldErrors.address}
            </span>
          ) : null}
        </div>
        <button
          className={styles.button}
          type="submit"
          disabled={isSubmitting}
        >
          {isSubmitting ? "Saving changes…" : "Save profile"}
        </button>
      </form>
    </section>
  );
}
