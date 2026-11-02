"use client";

import Link from "next/link";
import { useBusiness } from "../providers/BusinessProvider";
import { BusinessRouteGate } from "./BusinessRouteGate";
import { BusinessProfileForm } from "./BusinessProfileForm";
import styles from "./business.module.scss";

function ProfileContent() {
  const { business } = useBusiness();
  if (!business) {
    return null;
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <p className={styles.eyebrow}>Business settings</p>
          <h1 className={styles.title}>Business profile</h1>
          <p className={styles.description}>
            Manage the business details associated with your account.
          </p>
        </header>
        <BusinessProfileForm key={business.updatedAt} business={business} />
        <p className={styles.section}>
          <Link className={styles.link} href="/business/google-review">
            Manage Google review link and QR code
          </Link>
        </p>
      </div>
    </main>
  );
}

export function BusinessProfilePage() {
  return (
    <BusinessRouteGate>
      <ProfileContent />
    </BusinessRouteGate>
  );
}
