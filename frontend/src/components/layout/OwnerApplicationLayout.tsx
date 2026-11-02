"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { BusinessProvider, useBusiness } from "../../features/business/providers/BusinessProvider";
import { useAuth } from "../../features/auth/hooks/useAuth";
import styles from "./owner-application-layout.module.scss";

const navigation = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/customers", label: "Customers" },
  { href: "/transactions", label: "Transactions" },
  { href: "/feedback/questions", label: "Feedback questions" },
  { href: "/feedback/requests", label: "Feedback requests" },
  { href: "/business/profile", label: "Business profile" },
  { href: "/business/google-review", label: "Google review link" },
];

function OwnerNavigation({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { status } = useBusiness();
  const { user, logout } = useAuth();
  const [logoutError, setLogoutError] = useState<string | null>(null);

  async function handleLogout() {
    setLogoutError(null);
    try {
      await logout();
    } catch {
      setLogoutError(
        "The server could not confirm sign-out. The local session was cleared; sign in again to continue.",
      );
    }
  }

  return (
    <>
      {status === "ready" ? (
        <header className={styles.header}>
          <Link className={styles.brand} href="/dashboard">
            Review Engine
          </Link>
          <nav className={styles.navigation} aria-label="Owner navigation">
            {navigation.map((item) => {
              const active =
                pathname === item.href ||
                pathname.startsWith(`${item.href}/`) ||
                (item.href === "/feedback/requests" &&
                  pathname.startsWith("/feedback/analysis/"));
              return (
                <Link
                  className={active ? styles.activeLink : styles.link}
                  href={item.href}
                  key={item.href}
                  aria-current={active ? "page" : undefined}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
          <div className={styles.account}>
            {user?.name ? <span className={styles.ownerName}>{user.name}</span> : null}
            <button className={styles.logout} type="button" onClick={() => void handleLogout()}>
              Sign out
            </button>
          </div>
        </header>
      ) : null}
      {logoutError ? (
        <p className={styles.logoutError} role="alert">{logoutError}</p>
      ) : null}
      {children}
    </>
  );
}

export function OwnerApplicationLayout({ children }: { children: ReactNode }) {
  return (
    <BusinessProvider>
      <OwnerNavigation>{children}</OwnerNavigation>
    </BusinessProvider>
  );
}
