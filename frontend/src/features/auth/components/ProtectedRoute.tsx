"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "../hooks/useAuth";
import { getSafeReturnTo } from "../utils/return-to";
import styles from "./auth.module.scss";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status, isAuthenticated } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (status !== "unauthenticated" || typeof window === "undefined") {
      return;
    }

    const requested = `${pathname}${window.location.search}`;
    const returnTo = getSafeReturnTo(requested);
    const loginUrl = returnTo
      ? `/login?returnTo=${encodeURIComponent(returnTo)}`
      : "/login";
    router.replace(loginUrl);
  }, [pathname, router, status]);

  if (status !== "authenticated" || !isAuthenticated) {
    return (
      <div className={styles.loading} role="status" aria-live="polite">
        {status === "initializing" ? "Checking your session…" : "Redirecting to sign in…"}
      </div>
    );
  }

  return children;
}
