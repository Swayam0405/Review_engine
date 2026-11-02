"use client";

import Script from "next/script";
import { useCallback, useEffect, useRef, useState } from "react";
import { getAuthErrorMessage } from "../utils/auth-error-message";
import styles from "./auth.module.scss";

interface GoogleCredentialResponse {
  credential?: string;
}

interface GooglePromptNotification {
  isNotDisplayed: () => boolean;
  isSkippedMoment: () => boolean;
  isDismissedMoment: () => boolean;
}

export interface GoogleIdentitySdk {
  accounts: {
    id: {
      initialize: (options: {
        client_id: string;
        auto_select: false;
        callback: (response: GoogleCredentialResponse) => void;
      }) => void;
      prompt: (listener: (notification: GooglePromptNotification) => void) => void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentitySdk;
  }
}

interface GoogleSignInButtonProps {
  disabled?: boolean;
  onIdToken: (idToken: string) => Promise<void>;
}

export function GoogleSignInButton({
  disabled = false,
  onIdToken,
}: GoogleSignInButtonProps) {
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID?.trim();
  const [isReady, setIsReady] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(
    clientId ? null : "Google Sign-In is not configured.",
  );
  const onIdTokenRef = useRef(onIdToken);
  useEffect(() => {
    onIdTokenRef.current = onIdToken;
  }, [onIdToken]);

  const initializeGoogle = useCallback(() => {
    if (!clientId || !window.google) {
      setError("Google Sign-In could not be initialized.");
      return;
    }
    window.google.accounts.id.initialize({
      client_id: clientId,
      auto_select: false,
      callback: (response) => {
        if (!response.credential) {
          setIsSubmitting(false);
          setError("Google did not return an identity token. Try again.");
          return;
        }
        setError(null);
        setIsSubmitting(true);
        void onIdTokenRef
          .current(response.credential)
          .catch((cause: unknown) => {
            setError(
              getAuthErrorMessage(
                cause,
                "Google sign-in could not be completed. Try again.",
              ),
            );
          })
          .finally(() => setIsSubmitting(false));
      },
    });
    setIsReady(true);
    setError(null);
  }, [clientId]);

  function startGoogleSignIn() {
    if (!window.google || !isReady || disabled || isSubmitting) {
      return;
    }
    setError(null);
    setIsSubmitting(true);
    window.google.accounts.id.prompt((notification) => {
      if (notification.isDismissedMoment()) {
        setIsSubmitting(false);
        setError("Google sign-in was cancelled.");
      } else if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
        setIsSubmitting(false);
        setError("Google sign-in is unavailable. Try again or use email.");
      }
    });
  }

  return (
    <div>
      {clientId ? (
        <Script
          src="https://accounts.google.com/gsi/client"
          strategy="afterInteractive"
          onReady={initializeGoogle}
          onError={() => setError("Google Sign-In could not be loaded.")}
        />
      ) : null}
      <button
        className={`${styles.button} ${styles.secondaryButton}`}
        type="button"
        disabled={disabled || !clientId || !isReady || isSubmitting}
        onClick={startGoogleSignIn}
      >
        {isSubmitting
          ? "Connecting to Google…"
          : isReady
            ? "Continue with Google"
            : "Google Sign-In"}
      </button>
      {error ? (
        <p className={styles.error} role="alert" aria-live="polite">
          {error}
        </p>
      ) : null}
    </div>
  );
}
