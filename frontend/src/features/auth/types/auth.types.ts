import type { AuthResponse, AuthUser } from "../../../types/auth";

export type { AuthResponse, AuthUser };

export interface RegisterInput {
  name: string;
  email: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface VerifyEmailInput {
  email: string;
  otp: string;
}

export interface RegisterResponse {
  emailVerificationRequired: true;
  developmentOtp: string | null;
}

export interface ResendOtpResponse {
  developmentOtp: string | null;
}

export type AuthFieldErrors = Partial<
  Record<"name" | "email" | "password" | "confirmPassword" | "otp", string>
>;
