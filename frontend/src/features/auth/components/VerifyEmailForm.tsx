"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { ApiError } from "../../../lib/api/api-error";
import { useAuth } from "../hooks/useAuth";
import { resendVerificationCode, verifyEmail } from "../services/auth.service";
import { validateEmail, validateVerification } from "../schemas/auth.schemas";
import type { AuthFieldErrors } from "../types/auth.types";
import { getAuthErrorMessage } from "../utils/auth-error-message";
import { AuthField } from "./AuthField";
import { AuthShell } from "./AuthShell";
import styles from "./auth.module.scss";

const DEFAULT_RESEND_COOLDOWN_SECONDS = 60;

function getResendCooldownSeconds(): number | null {
  const configured = process.env.NEXT_PUBLIC_OTP_RESEND_COOLDOWN_SECONDS;
  if (!configured) {
    return DEFAULT_RESEND_COOLDOWN_SECONDS;
  }
  const seconds = Number(configured);
  return Number.isInteger(seconds) && seconds > 0 ? seconds : null;
}

export function VerifyEmailForm() {
  const {
    verificationEmail,
    registrationComplete,
    clearVerificationEmail,
  } = useAuth();
  const [email, setEmail] = useState(verificationEmail ?? "");
  const [otp, setOtp] = useState("");
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const verifyInFlight = useRef(false);
  const resendInFlight = useRef(false);

  useEffect(() => {
    if (resendCooldown <= 0) {
      return;
    }
    const timer = window.setTimeout(
      () => setResendCooldown((remaining) => Math.max(0, remaining - 1)),
      1000,
    );
    return () => window.clearTimeout(timer);
  }, [resendCooldown]);

  async function handleVerify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (verifyInFlight.current) {
      return;
    }
    const errors = validateVerification({ email, otp });
    setFieldErrors(errors);
    setFormError(null);
    setNotice(null);
    if (Object.keys(errors).length > 0) {
      return;
    }

    verifyInFlight.current = true;
    setIsSubmitting(true);
    try {
      await verifyEmail({ email: email.trim(), otp });
      clearVerificationEmail();
      setIsVerified(true);
      setNotice("Your email has been verified. You can now sign in.");
      setOtp("");
    } catch (error) {
      setFormError(
        getAuthErrorMessage(error, "We could not verify your email. Try again."),
      );
    } finally {
      verifyInFlight.current = false;
      setIsSubmitting(false);
    }
  }

  async function handleResend() {
    if (resendInFlight.current || resendCooldown > 0) {
      return;
    }
    const cooldown = getResendCooldownSeconds();
    if (cooldown === null) {
      setFormError("The verification resend cooldown is misconfigured.");
      return;
    }
    const emailError = validateEmail(email);
    if (emailError) {
      setFieldErrors((current) => ({ ...current, email: emailError }));
      setFormError(null);
      return;
    }

    resendInFlight.current = true;
    setIsResending(true);
    setFormError(null);
    setNotice(null);
    try {
      await resendVerificationCode(email.trim());
      setNotice(
        "If the account is eligible, a verification code will be sent.",
      );
      setResendCooldown(cooldown);
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        setResendCooldown(cooldown);
      }
      setFormError(
        getAuthErrorMessage(error, "We could not resend the code. Try again."),
      );
    } finally {
      resendInFlight.current = false;
      setIsResending(false);
    }
  }

  return (
    <AuthShell
      title={isVerified ? "Email verified" : "Verify your email"}
      description="Enter the six-digit code sent to your email address."
      footer={
        <>
          Already verified?{" "}
          <Link className={styles.textLink} href="/login">
            Sign in
          </Link>
        </>
      }
    >
      {registrationComplete ? (
        <p className={styles.notice} role="status">
          Your account was created. Check your email for the verification code.
        </p>
      ) : null}
      {notice ? (
        <p className={styles.success} role="status">
          {notice}
        </p>
      ) : null}
      <form className={styles.form} noValidate onSubmit={handleVerify}>
        {formError ? (
          <p className={styles.error} role="alert">
            {formError}
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
          id="otp"
          label="Six-digit verification code"
          type="otp"
          value={otp}
          autoComplete="one-time-code"
          error={fieldErrors.otp}
          onChange={(value) => setOtp(value.replace(/\D/g, "").slice(0, 6))}
        />
        <button className={styles.button} type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Verifying…" : "Verify email"}
        </button>
      </form>
      <button
        className={`${styles.button} ${styles.secondaryButton}`}
        type="button"
        disabled={isResending || resendCooldown > 0 || isVerified}
        onClick={() => void handleResend()}
      >
        {isResending
          ? "Sending…"
          : resendCooldown > 0
            ? `Resend code in ${resendCooldown}s`
            : "Resend code"}
      </button>
    </AuthShell>
  );
}
