// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import type { ReactNode } from "react";
import { ApiError } from "../../../lib/api/api-error";
import type { GoogleIdentitySdk } from "./GoogleSignInButton";
import { LoginForm } from "./LoginForm";
import { RegisterForm } from "./RegisterForm";
import { VerifyEmailForm } from "./VerifyEmailForm";

const mocks = vi.hoisted(() => ({
  registerAccount: vi.fn(),
  verifyEmail: vi.fn(),
  resendVerificationCode: vi.fn(),
  signInWithPassword: vi.fn(),
  signInWithGoogle: vi.fn(),
  signedInUser: { hasBusiness: true },
  setVerificationEmail: vi.fn(),
  clearVerificationEmail: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
}));

vi.mock("next/script", async () => {
  const React = await import("react");
  function MockScript({
    src,
    onReady,
  }: {
    src: string;
    onReady?: () => void;
  }) {
    React.useEffect(() => {
      if (window.google) {
        onReady?.();
      }
    }, [onReady]);
    return React.createElement("script", { "data-testid": "google-sdk", src });
  }
  return { default: MockScript };
});

vi.mock("next/link", async () => {
  const React = await import("react");
  return {
    default: ({
      href,
      children,
      ...props
    }: {
      href: string;
      children: ReactNode;
      [key: string]: unknown;
    }) => React.createElement("a", { ...props, href }, children),
  };
});

vi.mock("../services/auth.service", () => ({
  registerAccount: mocks.registerAccount,
  verifyEmail: mocks.verifyEmail,
  resendVerificationCode: mocks.resendVerificationCode,
}));

vi.mock("../hooks/useAuth", () => ({
  useAuth: () => ({
    signInWithPassword: mocks.signInWithPassword,
    signInWithGoogle: mocks.signInWithGoogle,
    user: null,
    setVerificationEmail: mocks.setVerificationEmail,
    clearVerificationEmail: mocks.clearVerificationEmail,
    verificationEmail: "owner@example.com",
    registrationComplete: false,
    isInitializing: false,
    sessionMessage: null,
  }),
}));

function fillRegistrationForm() {
  fireEvent.change(screen.getByLabelText("Name"), {
    target: { value: "Review Owner" },
  });
  fireEvent.change(screen.getByLabelText("Email"), {
    target: { value: "owner@example.com" },
  });
  fireEvent.change(screen.getByLabelText("Password"), {
    target: { value: "a-secure-password" },
  });
  fireEvent.change(screen.getByLabelText("Confirm password"), {
    target: { value: "a-secure-password" },
  });
}

function fillLoginForm() {
  fireEvent.change(screen.getByLabelText("Email"), {
    target: { value: "owner@example.com" },
  });
  fireEvent.change(screen.getByLabelText("Password"), {
    target: { value: "a-secure-password" },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  window.history.replaceState({}, "", "/login");
  vi.stubEnv("NEXT_PUBLIC_GOOGLE_CLIENT_ID", "public-client-id");
  mocks.registerAccount.mockResolvedValue({
    emailVerificationRequired: true,
    developmentOtp: null,
  });
  mocks.verifyEmail.mockResolvedValue(undefined);
  mocks.resendVerificationCode.mockResolvedValue({ developmentOtp: null });
  mocks.signInWithPassword.mockResolvedValue(mocks.signedInUser);
  mocks.signInWithGoogle.mockResolvedValue(mocks.signedInUser);
});

afterEach(() => {
  cleanup();
  delete window.google;
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe("registration form", () => {
  it("shows field-specific validation without sending an invalid request", async () => {
    render(<RegisterForm />);
    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "invalid" },
    });

    fireEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Name is required.")).toBeTruthy();
    expect(screen.getByText("Enter a valid email address.")).toBeTruthy();
    expect(screen.getByText("Password is required.")).toBeTruthy();
    expect(mocks.registerAccount).not.toHaveBeenCalled();
  });

  it("registers with backend fields and continues to email verification", async () => {
    render(<RegisterForm />);
    fillRegistrationForm();
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(mocks.registerAccount).toHaveBeenCalledTimes(1));
    expect(mocks.registerAccount).toHaveBeenCalledWith({
      name: "Review Owner",
      email: "owner@example.com",
      password: "a-secure-password",
    });
    expect(mocks.setVerificationEmail).toHaveBeenCalledWith(
      "owner@example.com",
      true,
    );
    expect(mocks.push).toHaveBeenCalledWith("/verify-email");
  });

  it("prevents duplicate registration submissions", async () => {
    let finishRegistration: (() => void) | undefined;
    mocks.registerAccount.mockImplementation(
      () =>
        new Promise((resolve) => {
          finishRegistration = () =>
            resolve({ emailVerificationRequired: true, developmentOtp: null });
        }),
    );
    render(<RegisterForm />);
    fillRegistrationForm();
    const form = screen.getByRole("button", { name: "Create account" }).closest("form");
    fireEvent.submit(form!);
    fireEvent.submit(form!);

    expect(mocks.registerAccount).toHaveBeenCalledTimes(1);
    finishRegistration?.();
  });

  it("shows a safe backend duplicate-account message", async () => {
    mocks.registerAccount.mockRejectedValue(
      new ApiError("CONFLICT", "An account already uses this email.", 409),
    );
    render(<RegisterForm />);
    fillRegistrationForm();
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("An account already uses this email.")).toBeTruthy();
  });
});

describe("email verification form", () => {
  it("submits a six-digit OTP and provides a success confirmation", async () => {
    render(<VerifyEmailForm />);
    fireEvent.change(screen.getByLabelText("Six-digit verification code"), {
      target: { value: "123abc4567" },
    });
    expect(
      (screen.getByLabelText("Six-digit verification code") as HTMLInputElement)
        .value,
    ).toBe("123456");
    fireEvent.click(screen.getByRole("button", { name: "Verify email" }));

    await screen.findByText("Your email has been verified. You can now sign in.");
    expect(mocks.verifyEmail).toHaveBeenCalledWith({
      email: "owner@example.com",
      otp: "123456",
    });
    expect(mocks.clearVerificationEmail).toHaveBeenCalledTimes(1);
  });

  it("shows field-level invalid-code and backend expiration errors", async () => {
    render(<VerifyEmailForm />);
    fireEvent.click(screen.getByRole("button", { name: "Verify email" }));
    expect(
      await screen.findByText("Enter the six-digit verification code."),
    ).toBeTruthy();
    expect(mocks.verifyEmail).not.toHaveBeenCalled();

    mocks.verifyEmail.mockRejectedValueOnce(
      new ApiError("BAD_REQUEST", "Verification code has expired.", 400),
    );
    fireEvent.change(screen.getByLabelText("Six-digit verification code"), {
      target: { value: "123456" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Verify email" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Verification code has expired.",
    );
  });

  it("confirms resend success and applies the documented cooldown", async () => {
    render(<VerifyEmailForm />);
    fireEvent.click(screen.getByRole("button", { name: "Resend code" }));
    expect(
      await screen.findByText(
        "If the account is eligible, a verification code will be sent.",
      ),
    ).toBeTruthy();
    expect(
      (screen.getByRole("button", { name: "Resend code in 60s" }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });

  it("reports backend rate limiting", async () => {
    mocks.resendVerificationCode.mockRejectedValueOnce(
      new ApiError("RATE_LIMITED", "Too many requests. Please wait and try again.", 429),
    );
    render(<VerifyEmailForm />);
    fireEvent.click(screen.getByRole("button", { name: "Resend code" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Too many requests. Please wait and try again.",
    );
    expect(mocks.resendVerificationCode).toHaveBeenCalledTimes(1);
  });
});

describe("login and Google sign-in", () => {
  it("signs in with email/password and navigates to the default destination", async () => {
    render(<LoginForm />);
    fillLoginForm();
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() =>
      expect(mocks.signInWithPassword).toHaveBeenCalledWith({
        email: "owner@example.com",
        password: "a-secure-password",
      }),
    );
    expect(mocks.replace).toHaveBeenCalledWith("/dashboard");
  });

  it("routes new users to onboarding and preserves safe internal return destinations", async () => {
    mocks.signInWithPassword.mockResolvedValueOnce({ hasBusiness: false });
    render(<LoginForm />);
    fillLoginForm();
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith("/business/onboarding"),
    );
    cleanup();

    mocks.replace.mockClear();
    window.history.replaceState({}, "", "/login?returnTo=%2Freviews%3Ftab%3Dnew");
    render(<LoginForm />);
    fillLoginForm();
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith("/reviews?tab=new"),
    );
  });

  it("shows invalid-credential and unverified-account errors", async () => {
    mocks.signInWithPassword.mockRejectedValueOnce(
      new ApiError("UNAUTHORIZED", "Invalid email or password.", 401),
    );
    render(<LoginForm />);
    fillLoginForm();
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Invalid email or password.",
    );

    mocks.signInWithPassword.mockRejectedValueOnce(
      new ApiError(
        "FORBIDDEN",
        "Email verification is required before login.",
        403,
      ),
    );
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Email verification is required before login.",
    );
    expect(
      await screen.findByRole("link", { name: "Verify your email address" }),
    ).toBeTruthy();
  });

  it("shows missing Google client configuration clearly", () => {
    vi.stubEnv("NEXT_PUBLIC_GOOGLE_CLIENT_ID", "");
    render(<LoginForm />);
    expect(screen.getByRole("alert").textContent).toContain(
      "Google Sign-In is not configured.",
    );
    expect(
      (screen.getByRole("button", { name: "Google Sign-In" }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });

  it("exchanges the Google ID token and reports a dismissed popup", async () => {
    let credentialCallback:
      | ((response: { credential?: string }) => void)
      | undefined;
    const sdk: GoogleIdentitySdk = {
      accounts: {
        id: {
          initialize: ({ callback }) => {
            credentialCallback = callback;
          },
          prompt: (listener) =>
            listener({
              isNotDisplayed: () => false,
              isSkippedMoment: () => false,
              isDismissedMoment: () => true,
            }),
        },
      },
    };
    window.google = sdk;
    render(<LoginForm />);

    const googleButton = await screen.findByRole("button", {
      name: "Continue with Google",
    });
    fireEvent.click(googleButton);
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Google sign-in was cancelled.",
    );

    sdk.accounts.id.prompt = () =>
      credentialCallback?.({ credential: "google-id-token" });
    fireEvent.click(googleButton);
    await waitFor(() =>
      expect(mocks.signInWithGoogle).toHaveBeenCalledWith("google-id-token"),
    );
    expect(mocks.replace).toHaveBeenCalledWith("/dashboard");
    delete window.google;
  });

  describe("logout recovery messaging", () => {
    it("shows that server-side logout could not be confirmed", () => {
      window.history.replaceState({}, "", "/login?logout=unconfirmed");
      render(<LoginForm />);
      expect(screen.getByRole("alert").textContent).toContain(
        "server could not confirm sign-out",
      );
    });
  });

  it("reports Google backend token failures", async () => {
    let credentialCallback:
      | ((response: { credential?: string }) => void)
      | undefined;
    window.google = {
      accounts: {
        id: {
          initialize: ({ callback }) => {
            credentialCallback = callback;
          },
          prompt: (listener) =>
            listener({
              isNotDisplayed: () => false,
              isSkippedMoment: () => false,
              isDismissedMoment: () => false,
            }),
        },
      },
    };
    render(<LoginForm />);
    mocks.signInWithGoogle.mockRejectedValue(
      new ApiError("UNAUTHORIZED", "Google identity token is invalid.", 401),
    );
    fireEvent.click(
      await screen.findByRole("button", { name: "Continue with Google" }),
    );
    credentialCallback?.({ credential: "invalid-google-id-token" });

    expect((await screen.findByRole("alert")).textContent).toContain(
      "Google identity token is invalid.",
    );
    delete window.google;
  });
});
