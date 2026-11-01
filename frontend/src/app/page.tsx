import styles from "./page.module.scss";

export default function Home() {
  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="welcome-title">
        <p className={styles.eyebrow}>Review Engine</p>
        <h1 className={styles.title} id="welcome-title">
          The frontend foundation is ready.
        </h1>
        <p className={styles.description}>
          Phase 7 development can build on this Next.js application while
          keeping customer feedback and review workflows connected to the
          Spring Boot backend.
        </p>
        <p className={styles.status} role="status">
          <span className={styles.statusIndicator} aria-hidden="true" />
          Application scaffold ready
        </p>
      </section>
    </main>
  );
}
