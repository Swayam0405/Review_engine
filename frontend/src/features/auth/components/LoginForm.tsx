"use client";

import { useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "../hooks/useAuth";
import { validateLogin } from "../schemas/auth.schemas";
import type { AuthFieldErrors } from "../types/auth.types";
import { getAuthErrorMessage } from "../utils/auth-error-message";
import { getSafeReturnTo } from "../utils/return-to";
import { AuthField } from "./AuthField";
import { AuthShell } from "./AuthShell";
import { GoogleSignInButton } from "./GoogleSignInButton";
import styles from "./auth.module.scss";

function getReturnDestination(defaultDestination: string): string {
  if (typeof window === "undefined") {
    return defaultDestination;
  }
  const requested = new URLSearchParams(window.location.search).get("returnTo");
  return getSafeReturnTo(requested) ?? defaultDestination;
}

export function LoginForm() {
  const router = useRouter();
  const {
    signInWithPassword,
    signInWithGoogle,
    isInitializing,
    sessionMessage,
    setVerificationEmail,
  } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [logoutWarning] = useState(() =>
    typeof window !== "undefined" &&
    new URLSearchParams(window.location.search).get("logout") === "unconfirmed"
      ? "Your local session was cleared, but the server could not confirm sign-out. Sign in again before using this session."
      : null,
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submissionInFlight = useRef(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submissionInFlight.current || isInitializing) {
      return;
    }
    const errors = validateLogin({ email, password });
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      return;
    }

    submissionInFlight.current = true;
    setIsSubmitting(true);
    try {
      const signedInUser = await signInWithPassword({
        email: email.trim(),
        password,
      });
      router.replace(
        getReturnDestination(
          signedInUser.hasBusiness
            ? "/dashboard"
            : "/business/onboarding",
        ),
      );
    } catch (error) {
      setFormError(getAuthErrorMessage(error, "We could not sign you in. Try again."));
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
  }

  async function handleGoogleToken(idToken: string) {
    const signedInUser = await signInWithGoogle(idToken);
    router.replace(
      getReturnDestination(
        signedInUser.hasBusiness
          ? "/dashboard"
          : "/business/onboarding",
      ),
    );
  }

  const requiresVerification =
    formError?.toLowerCase().includes("verification") ?? false;

  return (
    <AuthShell
      title="Welcome back"
      description="Sign in to continue to Review Engine."
      footer={
        <>
          New to Review Engine?{" "}
          <Link className={styles.textLink} href="/register">
            Create an account
          </Link>
        </>
      }
    >
      {sessionMessage ? (
        <p className={styles.notice} role="status">
          {sessionMessage}
        </p>
      ) : null}
      {logoutWarning ? (
        <p className={styles.notice} role="alert">
          {logoutWarning}
        </p>
      ) : null}
      <form className={styles.form} noValidate onSubmit={handleSubmit}>
        {formError ? (
          <p className={styles.error} role="alert">
            {formError}
          </p>
        ) : null}
        {requiresVerification ? (
          <p className={styles.notice}>
            <Link
              className={styles.textLink}
              href="/verify-email"
              onClick={() => setVerificationEmail(email.trim())}
            >
              Verify your email address
            </Link>
          </p>
        ) : null}
        <AuthField
          id="email"
          label="Email"
          type="email"
          value={email}
          autoComplete="email"
          error={fieldErrors.email}
          onChange={setEmail}
        />
        <AuthField
          id="password"
          label="Password"
          type="password"
          value={password}
          autoComplete="current-password"
          error={fieldErrors.password}
          onChange={setPassword}
        />
        <button
          className={styles.button}
          type="submit"
          disabled={isInitializing || isSubmitting}
        >
          {isInitializing
            ? "Checking session…"
            : isSubmitting
              ? "Signing in…"
              : "Sign in"}
        </button>
      </form>
      <p className={styles.divider}>or</p>
      <GoogleSignInButton
        disabled={isInitializing || isSubmitting}
        onIdToken={handleGoogleToken}
      />
    </AuthShell>
  );
}
