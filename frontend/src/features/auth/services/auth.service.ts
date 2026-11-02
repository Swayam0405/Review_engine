import { apiRequest, logoutSession } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type { AccountStatus, AuthResponse, AuthUser } from "../../../types/auth";
import type {
  LoginInput,
  RegisterInput,
  RegisterResponse,
  ResendOtpResponse,
  VerifyEmailInput,
} from "../types/auth.types";

const AUTH_PATH = "/api/auth";
const ACCOUNT_STATUSES = new Set<string>([
  "PENDING_VERIFICATION",
  "ACTIVE",
  "DISABLED",
]);

export async function registerAccount(
  input: RegisterInput,
): Promise<RegisterResponse> {
  const response = await apiRequest<RegisterResponse>(`${AUTH_PATH}/register`, {
    method: "POST",
    body: input,
  });
  if (
    !isRecord(response) ||
    response.emailVerificationRequired !== true ||
    !isOptionalString(response.developmentOtp)
  ) {
    throw invalidAuthResponse();
  }
  return {
    emailVerificationRequired: true,
    developmentOtp: response.developmentOtp,
  };
}

export async function verifyEmail(input: VerifyEmailInput): Promise<void> {
  await apiRequest<null>(`${AUTH_PATH}/verify-email`, {
    method: "POST",
    body: input,
  });
}

export async function resendVerificationCode(
  email: string,
): Promise<ResendOtpResponse> {
  const response = await apiRequest<ResendOtpResponse>(
    `${AUTH_PATH}/resend-otp`,
    {
      method: "POST",
      body: { email },
    },
  );
  if (!isRecord(response) || !isOptionalString(response.developmentOtp)) {
    throw invalidAuthResponse();
  }
  return { developmentOtp: response.developmentOtp };
}

export async function loginWithPassword(
  input: LoginInput,
): Promise<AuthResponse> {
  const response = await apiRequest<unknown>(`${AUTH_PATH}/login`, {
    method: "POST",
    body: input,
    credentials: "include",
  });
  return parseAuthResponse(response);
}

export async function loginWithGoogle(idToken: string): Promise<AuthResponse> {
  if (!idToken || idToken.length > 8192) {
    throw new ApiError(
      "CONFIGURATION_ERROR",
      "The Google identity token is invalid.",
    );
  }
  const response = await apiRequest<unknown>(`${AUTH_PATH}/google`, {
    method: "POST",
    body: { idToken },
    credentials: "include",
  });
  return parseAuthResponse(response);
}

export async function getCurrentUser(): Promise<AuthUser> {
  const response = await apiRequest<unknown>(`${AUTH_PATH}/me`, {
    auth: "required",
  });
  return parseAuthUser(response);
}

export function logout(): Promise<void> {
  return logoutSession();
}

function parseAuthResponse(value: unknown): AuthResponse {
  if (!isRecord(value) || typeof value.accessToken !== "string") {
    throw invalidAuthResponse();
  }
  if (value.tokenType !== "Bearer" || value.accessToken.trim().length === 0) {
    throw invalidAuthResponse();
  }
  return {
    accessToken: value.accessToken,
    tokenType: value.tokenType,
    user: parseAuthUser(value.user),
  };
}

function parseAuthUser(value: unknown): AuthUser {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.name !== "string" ||
    typeof value.email !== "string" ||
    !isAccountStatus(value.accountStatus) ||
    typeof value.emailVerified !== "boolean" ||
    typeof value.hasBusiness !== "boolean"
  ) {
    throw invalidAuthResponse();
  }
  return {
    id: value.id,
    name: value.name,
    email: value.email,
    accountStatus: value.accountStatus,
    emailVerified: value.emailVerified,
    hasBusiness: value.hasBusiness,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isOptionalString(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

function isAccountStatus(value: unknown): value is AccountStatus {
  return (
    typeof value === "string" &&
    ACCOUNT_STATUSES.has(value)
  );
}

function invalidAuthResponse(): ApiError {
  return new ApiError(
    "INVALID_RESPONSE",
    "The server returned an invalid authentication response.",
  );
}
