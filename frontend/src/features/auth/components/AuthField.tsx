import styles from "./auth.module.scss";

interface AuthFieldProps {
  id: string;
  label: string;
  type?: "email" | "password" | "text" | "otp";
  value: string;
  autoComplete?: string;
  error?: string;
  onChange: (value: string) => void;
}

export function AuthField({
  id,
  label,
  type = "text",
  value,
  autoComplete,
  error,
  onChange,
}: AuthFieldProps) {
  const inputType = type === "otp" ? "text" : type;
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      <input
        className={`${styles.input} ${error ? styles.inputError : ""}`}
        id={id}
        name={id}
        type={inputType}
        value={value}
        autoComplete={autoComplete}
        required
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        inputMode={type === "otp" ? "numeric" : undefined}
        maxLength={type === "otp" ? 6 : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error ? (
        <span className={styles.fieldError} id={`${id}-error`}>
          {error}
        </span>
      ) : null}
    </div>
  );
}
