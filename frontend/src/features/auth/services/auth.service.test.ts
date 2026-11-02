// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/api/api-error";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from "../../../lib/auth/access-token-store";
import {
  getCurrentUser,
  loginWithGoogle,
  loginWithPassword,
  registerAccount,
  resendVerificationCode,
  verifyEmail,
} from "./auth.service";

const user = {
  id: "owner-id",
  name: "Review Owner",
  email: "owner@example.com",
  accountStatus: "ACTIVE",
  emailVerified: true,
  hasBusiness: false,
};

function envelope(data: unknown, success = true, message = "OK") {
  return { success, message, data, timestamp: "2026-01-01T00:00:00Z" };
}

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function pathOf(input: RequestInfo | URL): string {
  return new URL(String(input)).pathname;
}

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.test");
  clearAccessToken();
});

afterEach(() => {
  clearAccessToken();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("authentication API service", () => {
  it("sends the exact registration DTO and accepts verification-required response", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope({ emailVerificationRequired: true, developmentOtp: null })),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      registerAccount({
        name: "Review Owner",
        email: "owner@example.com",
        password: "a-secure-pass",
      }),
    ).resolves.toEqual({ emailVerificationRequired: true, developmentOtp: null });
    expect(pathOf(fetchMock.mock.calls[0]![0])).toBe("/api/auth/register");
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      name: "Review Owner",
      email: "owner@example.com",
      password: "a-secure-pass",
    });
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe("omit");
  });

  it("submits the email and six-digit OTP and supports generic resend success", async () => {
    const fetchMock = vi.fn<typeof fetch>(async (input) =>
      pathOf(input) === "/api/auth/verify-email"
        ? jsonResponse(envelope(null))
        : jsonResponse(envelope({ developmentOtp: null })),
    );
    vi.stubGlobal("fetch", fetchMock);

    await verifyEmail({ email: "owner@example.com", otp: "123456" });
    await expect(resendVerificationCode("owner@example.com")).resolves.toEqual({
      developmentOtp: null,
    });
    expect(pathOf(fetchMock.mock.calls[0]![0])).toBe("/api/auth/verify-email");
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      email: "owner@example.com",
      otp: "123456",
    });
    expect(pathOf(fetchMock.mock.calls[1]![0])).toBe("/api/auth/resend-otp");
  });

  it("includes credentials on login so the browser accepts backend auth cookies", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope({
        accessToken: "memory-only-token",
        tokenType: "Bearer",
        user,
      })),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      loginWithPassword({ email: "owner@example.com", password: "secret" }),
    ).resolves.toMatchObject({ accessToken: "memory-only-token", user });
    expect(pathOf(fetchMock.mock.calls[0]![0])).toBe("/api/auth/login");
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe("include");
  });

  it("sends only Google's identity token and includes credentials", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope({
        accessToken: "google-access",
        tokenType: "Bearer",
        user,
      })),
    );
    vi.stubGlobal("fetch", fetchMock);

    await loginWithGoogle("verified-google-id-token");
    expect(pathOf(fetchMock.mock.calls[0]![0])).toBe("/api/auth/google");
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      idToken: "verified-google-id-token",
    });
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe("include");
  });

  it("rejects malformed auth responses and preserves backend errors", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope({ accessToken: "", tokenType: "Bearer", user })),
    );
    vi.stubGlobal("fetch", fetchMock);
    await expect(
      loginWithPassword({ email: "owner@example.com", password: "secret" }),
    ).rejects.toMatchObject({ code: "INVALID_RESPONSE" });

    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () =>
        jsonResponse(
          envelope(null, false, "Email verification is required before login."),
          403,
        ),
      ),
    );
    await expect(
      loginWithPassword({ email: "owner@example.com", password: "secret" }),
    ).rejects.toMatchObject({
      code: "FORBIDDEN",
      message: "Email verification is required before login.",
    });
  });

  it("restores the current user with bearer auth and stores no persistent token", async () => {
    setAccessToken("private-memory-token");
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(envelope(user)));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getCurrentUser()).resolves.toEqual(user);
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("Authorization"))
      .toMatch(/^Bearer /);
    expect(getAccessToken()).toBe("private-memory-token");
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it("rejects an oversized Google identity token before sending a request", async () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    await expect(loginWithGoogle("x".repeat(8193))).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
