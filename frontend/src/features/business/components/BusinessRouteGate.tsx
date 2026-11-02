"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { ProtectedRoute } from "../../auth/components/ProtectedRoute";
import { useAuth } from "../../auth/hooks/useAuth";
import { getSafeReturnTo } from "../../auth/utils/return-to";
import { useBusiness } from "../providers/BusinessProvider";
import styles from "./business.module.scss";

interface BusinessRouteGateProps {
  children: ReactNode;
  onboarding?: boolean;
}

export function BusinessRouteGate({
  children,
  onboarding = false,
}: BusinessRouteGateProps) {
  const { status: authStatus } = useAuth();
  const {
    business,
    status: businessStatus,
    error,
    retryBusinessLoad,
  } = useBusiness();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (authStatus !== "authenticated" || businessStatus === "loading") {
      return;
    }

    if (onboarding && businessStatus === "ready") {
      const returnTo =
        typeof window === "undefined"
          ? null
          : getSafeReturnTo(
              new URLSearchParams(window.location.search).get("returnTo"),
            );
      router.replace(
        returnTo && returnTo !== "/business/onboarding"
          ? returnTo
          : "/business/profile",
      );
      return;
    }

    if (!onboarding && businessStatus === "missing") {
      const returnTo = getSafeReturnTo(pathname);
      router.replace(
        returnTo
          ? `/business/onboarding?returnTo=${encodeURIComponent(returnTo)}`
          : "/business/onboarding",
      );
    }
  }, [authStatus, businessStatus, onboarding, pathname, router]);

  return (
    <ProtectedRoute>
      {businessStatus === "loading" ? (
        <div className={styles.state} role="status" aria-live="polite">
          Loading your business…
        </div>
      ) : businessStatus === "error" ? (
        <main className={styles.state}>
          <section className={styles.stateCard} role="alert">
            <h1>Business details unavailable</h1>
            <p>{error}</p>
            <button
              className={styles.button}
              type="button"
              onClick={retryBusinessLoad}
            >
              Try again
            </button>
          </section>
        </main>
      ) : onboarding ? (
        businessStatus === "missing" ? (
          children
        ) : (
          <div className={styles.state} role="status" aria-live="polite">
            Opening your business profile…
          </div>
        )
      ) : businessStatus === "ready" && business ? (
        children
      ) : (
        <div className={styles.state} role="status" aria-live="polite">
          Redirecting to business setup…
        </div>
      )}
    </ProtectedRoute>
  );
}
