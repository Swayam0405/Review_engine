"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "../features/auth/hooks/useAuth";
import { getSafeReturnTo } from "../features/auth/utils/return-to";
import styles from "./page.module.scss";

export default function Home() {
  const router = useRouter();
  const { status, user } = useAuth();

  useEffect(() => {
    if (status === "initializing") return;
    const params = new URLSearchParams(window.location.search);
    const requested = getSafeReturnTo(params.get("returnTo"));
    if (status === "unauthenticated") {
      router.replace(
        requested ? `/login?returnTo=${encodeURIComponent(requested)}` : "/login",
      );
      return;
    }

    const destination = user?.hasBusiness
      ? requested ?? "/dashboard"
      : requested
        ? `/business/onboarding?returnTo=${encodeURIComponent(requested)}`
        : "/business/onboarding";
    router.replace(destination);
  }, [router, status, user]);

  return (
    <main className={styles.page}>
      <p className={styles.status} role="status">
        <span className={styles.statusIndicator} aria-hidden="true" />
        Preparing your Review Engine workspace…
      </p>
    </main>
  );
}
