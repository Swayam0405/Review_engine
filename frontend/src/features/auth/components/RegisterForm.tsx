"use client";

import { useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  registerAccount,
} from "../services/auth.service";
import { validateRegistration } from "../schemas/auth.schemas";
import type { AuthFieldErrors } from "../types/auth.types";
import { useAuth } from "../hooks/useAuth";
import { getAuthErrorMessage } from "../utils/auth-error-message";
import { AuthField } from "./AuthField";
import { AuthShell } from "./AuthShell";
import styles from "./auth.module.scss";

export function RegisterForm() {
  const router = useRouter();
  const { setVerificationEmail } = useAuth();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submissionInFlight = useRef(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submissionInFlight.current) {
      return;
    }

    const errors = validateRegistration({ name, email, password, confirmPassword });
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      return;
    }

    submissionInFlight.current = true;
    setIsSubmitting(true);
    try {
      await registerAccount({ name: name.trim(), email: email.trim(), password });
      setVerificationEmail(email.trim(), true);
      router.push("/verify-email");
    } catch (error) {
      setFormError(
        getAuthErrorMessage(error, "We could not create your account. Try again."),
      );
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
  }

  return (
    <AuthShell
      title="Create your account"
      description="Register to start managing customer feedback."
      footer={
        <>
          Already have an account?{" "}
          <Link className={styles.textLink} href="/login">
            Sign in
          </Link>
        </>
      }
    >
      <form className={styles.form} noValidate onSubmit={handleSubmit}>
        {formError ? (
          <p className={styles.error} role="alert">
            {formError}
          </p>
        ) : null}
        <AuthField
          id="name"
          label="Name"
          value={name}
          autoComplete="name"
          error={fieldErrors.name}
          onChange={setName}
        />
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
          autoComplete="new-password"
          error={fieldErrors.password}
          onChange={setPassword}
        />
        <AuthField
          id="confirmPassword"
          label="Confirm password"
          type="password"
          value={confirmPassword}
          autoComplete="new-password"
          error={fieldErrors.confirmPassword}
          onChange={setConfirmPassword}
        />
        <button className={styles.button} type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Creating account…" : "Create account"}
        </button>
      </form>
    </AuthShell>
  );
}
