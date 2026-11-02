import type { AuthFieldErrors, LoginInput, RegisterInput, VerifyEmailInput } from "../types/auth.types";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateEmail(email: string): string | undefined {
  const normalized = email.trim();
  if (!normalized) {
    return "Email is required.";
  }
  if (normalized.length > 320 || !EMAIL_PATTERN.test(normalized)) {
    return "Enter a valid email address.";
  }
  return undefined;
}

export function validateRegistration(
  input: RegisterInput & { confirmPassword: string },
): AuthFieldErrors {
  const errors: AuthFieldErrors = {};
  const name = input.name.trim();

  if (!name) {
    errors.name = "Name is required.";
  } else if (name.length > 120) {
    errors.name = "Name must be 120 characters or fewer.";
  }

  const emailError = validateEmail(input.email);
  if (emailError) {
    errors.email = emailError;
  }

  if (!input.password) {
    errors.password = "Password is required.";
  } else if (input.password.length < 12 || input.password.length > 72) {
    errors.password = "Password must be between 12 and 72 characters.";
  }

  if (!input.confirmPassword) {
    errors.confirmPassword = "Confirm your password.";
  } else if (input.password !== input.confirmPassword) {
    errors.confirmPassword = "Passwords do not match.";
  }

  return errors;
}

export function validateLogin(input: LoginInput): AuthFieldErrors {
  const errors: AuthFieldErrors = {};
  const emailError = validateEmail(input.email);
  if (emailError) {
    errors.email = emailError;
  }
  if (!input.password.trim()) {
    errors.password = "Password is required.";
  }
  return errors;
}

export function validateVerification(input: VerifyEmailInput): AuthFieldErrors {
  const errors: AuthFieldErrors = {};
  const emailError = validateEmail(input.email);
  if (emailError) {
    errors.email = emailError;
  }
  if (!/^\d{6}$/.test(input.otp)) {
    errors.otp = "Enter the six-digit verification code.";
  }
  return errors;
}
