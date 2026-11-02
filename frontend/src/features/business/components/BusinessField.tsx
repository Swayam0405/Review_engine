import styles from "./business.module.scss";

interface BusinessFieldProps {
  id: string;
  label: string;
  value: string;
  required?: boolean;
  type?: "text" | "url" | "timezone";
  error?: string;
  onChange: (value: string) => void;
}

export function BusinessField({
  id,
  label,
  value,
  required = true,
  type = "text",
  error,
  onChange,
}: BusinessFieldProps) {
  const isTimezone = type === "timezone";
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
        {required ? " *" : " (optional)"}
      </label>
      <input
        className={`${styles.input} ${error ? styles.invalid : ""}`}
        id={id}
        name={id}
        type={isTimezone ? "text" : type}
        value={value}
        required={required}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        list={isTimezone ? "iana-timezones" : undefined}
        autoComplete={isTimezone ? "off" : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {isTimezone ? (
        <datalist id="iana-timezones">
          <option value="UTC" />
          <option value="America/New_York" />
          <option value="Europe/London" />
          <option value="Asia/Kolkata" />
          <option value="Asia/Tokyo" />
          <option value="Australia/Sydney" />
        </datalist>
      ) : null}
      {error ? (
        <span className={styles.fieldError} id={`${id}-error`}>
          {error}
        </span>
      ) : null}
    </div>
  );
}
