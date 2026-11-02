import type { ReactNode } from "react";
import styles from "./auth.module.scss";

interface AuthShellProps {
  title: string;
  description: string;
  children: ReactNode;
  footer?: ReactNode;
}

export function AuthShell({
  title,
  description,
  children,
  footer,
}: AuthShellProps) {
  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="auth-title">
        <p className={styles.brand}>Review Engine</p>
        <h1 className={styles.title} id="auth-title">
          {title}
        </h1>
        <p className={styles.description}>{description}</p>
        {children}
        {footer ? <div className={styles.footer}>{footer}</div> : null}
      </section>
    </main>
  );
}
