"use client";

import { useRef, useState, type FormEvent } from "react";
import { useBusiness } from "../providers/BusinessProvider";
import { updateGoogleReviewUrl } from "../services/business.service";
import { validateGoogleReviewUrl } from "../schemas/business.schemas";
import { BusinessRouteGate } from "./BusinessRouteGate";
import { GoogleReviewQr } from "./GoogleReviewQr";
import styles from "./business.module.scss";

function GoogleReviewSettings() {
  const { business, refreshBusiness } = useBusiness();
  const [url, setUrl] = useState(business?.googleReviewUrl ?? "");
  const [error, setError] = useState<string | null>(null);
  const [urlError, setUrlError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const submissionInFlight = useRef(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submissionInFlight.current) {
      return;
    }
    const validationError = validateGoogleReviewUrl(url);
    if (!url.trim()) {
      setUrlError("Google review URL is required.");
      return;
    }
    setUrlError(validationError ?? null);
    setError(null);
    setSuccess(null);
    if (validationError) {
      return;
    }

    submissionInFlight.current = true;
    setIsSaving(true);
    try {
      const updated = await updateGoogleReviewUrl({
        googleReviewUrl: url.trim(),
      });
      await refreshBusiness();
      setUrl(updated.googleReviewUrl ?? "");
      setSuccess("Google review URL updated.");
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Google review URL could not be saved.",
      );
    } finally {
      submissionInFlight.current = false;
      setIsSaving(false);
    }
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <p className={styles.eyebrow}>Review settings</p>
          <h1 className={styles.title}>Google review link</h1>
          <p className={styles.description}>
            Add the review URL provided by Google. Review Engine will not
            create or guess this link.
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
            <div className={styles.field}>
              <label className={styles.label} htmlFor="google-review-url">
                Google review URL *
              </label>
              <input
                className={`${styles.input} ${urlError ? styles.invalid : ""}`}
                id="google-review-url"
                name="googleReviewUrl"
                type="url"
                value={url}
                required
                aria-invalid={Boolean(urlError)}
                aria-describedby={
                  urlError ? "google-review-url-error" : "review-url-help"
                }
                onChange={(event) => setUrl(event.target.value)}
              />
              {urlError ? (
                <span
                  className={styles.fieldError}
                  id="google-review-url-error"
                >
                  {urlError}
                </span>
              ) : (
                <span className={styles.muted} id="review-url-help">
                  Supported HTTPS Google review links only.
                </span>
              )}
            </div>
            <button
              className={styles.button}
              type="submit"
              disabled={isSaving}
            >
              {isSaving ? "Saving link…" : "Save review URL"}
            </button>
            {business?.googleReviewUrl ? (
              <p className={styles.muted}>
                Current link:{" "}
                <a
                  className={styles.link}
                  href={business.googleReviewUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  {business.googleReviewUrl}
                </a>
              </p>
            ) : (
              <p className={styles.notice}>No review URL is configured yet.</p>
            )}
          </form>
          <GoogleReviewQr
            key={business?.updatedAt}
            configured={Boolean(business?.googleReviewUrl)}
          />
        </section>
      </div>
    </main>
  );
}

export function GoogleReviewUrlForm() {
  return (
    <BusinessRouteGate>
      <GoogleReviewSettings />
    </BusinessRouteGate>
  );
}
