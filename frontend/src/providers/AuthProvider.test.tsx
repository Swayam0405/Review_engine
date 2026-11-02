// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { useState } from "react";
import { ApiError } from "../lib/api/api-error";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from "../lib/auth/access-token-store";
import { ProtectedRoute } from "../features/auth/components/ProtectedRoute";
import { useAuthContext } from "./AuthProvider";
import { AuthProvider } from "./AuthProvider";

const mocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  loginWithGoogle: vi.fn(),
  loginWithPassword: vi.fn(),
  logout: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("../features/auth/services/auth.service", () => ({
  getCurrentUser: mocks.getCurrentUser,
  loginWithGoogle: mocks.loginWithGoogle,
  loginWithPassword: mocks.loginWithPassword,
  logout: mocks.logout,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/private/reports",
}));

const currentUser = {
  id: "owner-id",
  name: "Review Owner",
  email: "owner@example.com",
  accountStatus: "ACTIVE" as const,
  emailVerified: true,
  hasBusiness: false,
};

const authResponse = {
  accessToken: "in-memory-access-token",
  tokenType: "Bearer",
  user: currentUser,
};

function AuthProbe() {
  const auth = useAuthContext();
  const [logoutError, setLogoutError] = useState<string | null>(null);

  return (
    <div>
      <p data-testid="auth-status">{auth.status}</p>
      <p data-testid="user-email">{auth.user?.email ?? "no-user"}</p>
      {logoutError ? <p role="alert">{logoutError}</p> : null}
      <button
        type="button"
        onClick={() =>
          void auth.signInWithPassword({
            email: "owner@example.com",
            password: "a-secure-password",
          })
        }
      >
        Sign in through provider
      </button>
      <button
        type="button"
        onClick={() => {
          void auth.logout().catch(() => setLogoutError("Logout could not be confirmed."));
        }}
      >
        Log out
      </button>
    </div>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  clearAccessToken();
  mocks.getCurrentUser.mockResolvedValue(currentUser);
  mocks.loginWithPassword.mockResolvedValue(authResponse);
  mocks.loginWithGoogle.mockResolvedValue(authResponse);
  mocks.logout.mockResolvedValue(undefined);
});

afterEach(() => {
  cleanup();
  clearAccessToken();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

describe("authentication provider", () => {
  it("restores the user once and completes initialization", async () => {
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("auth-status").textContent).toBe("authenticated"),
    );
    expect(screen.getByTestId("user-email").textContent).toBe("owner@example.com");
    expect(mocks.getCurrentUser).toHaveBeenCalledTimes(1);
  });

  it("ends initialization safely when session restoration fails", async () => {
    setAccessToken("stale-memory-token");
    mocks.getCurrentUser.mockRejectedValue(
      new ApiError("INVALID_RESPONSE", "The server returned an invalid response."),
    );
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("auth-status").textContent).toBe("unauthenticated"),
    );
    expect(getAccessToken()).toBeNull();
  });

  it("stores a successful login token only in runtime memory", async () => {
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );
    await screen.findByText("authenticated");

    fireEvent.click(screen.getByRole("button", { name: "Sign in through provider" }));
    await waitFor(() =>
      expect(getAccessToken()).toBe("in-memory-access-token"),
    );
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(screen.getByTestId("auth-status").textContent).toBe("authenticated");
  });

  it("clears user state and token on logout and reports server failure honestly", async () => {
    mocks.logout.mockRejectedValue(new ApiError("NETWORK_ERROR", "Offline."));
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );
    await screen.findByText("authenticated");

    fireEvent.click(screen.getByRole("button", { name: "Log out" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Logout could not be confirmed.",
    );
    expect(screen.getByTestId("auth-status").textContent).toBe("unauthenticated");
    expect(screen.getByTestId("user-email").textContent).toBe("no-user");
    expect(getAccessToken()).toBeNull();
    expect(mocks.replace).toHaveBeenCalledWith("/login?logout=unconfirmed");
  });

  it("does not restore stale user state after logout races with restoration", async () => {
    let finishRestore: ((user: typeof currentUser) => void) | undefined;
    mocks.getCurrentUser.mockImplementation(
      () => new Promise((resolve) => (finishRestore = resolve)),
    );
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Log out" }));
    await waitFor(() =>
      expect(screen.getByTestId("auth-status").textContent).toBe("unauthenticated"),
    );
    await act(async () => {
      finishRestore?.(currentUser);
    });

    expect(screen.getByTestId("user-email").textContent).toBe("no-user");
    expect(getAccessToken()).toBeNull();
  });

  it("does not render protected content before restoration and redirects safely", async () => {
    let rejectRestore: ((error: Error) => void) | undefined;
    mocks.getCurrentUser.mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectRestore = reject;
        }),
    );
    render(
      <AuthProvider>
        <ProtectedRoute>
          <p>Private account data</p>
        </ProtectedRoute>
      </AuthProvider>,
    );

    expect(screen.queryByText("Private account data")).toBeNull();
    expect(screen.getByRole("status").textContent).toBe("Checking your session…");
    await act(async () => {
      rejectRestore?.(new ApiError("UNAUTHORIZED", "Authentication is required.", 401));
    });
    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith(
        "/login?returnTo=%2Fprivate%2Freports",
      ),
    );
    expect(screen.queryByText("Private account data")).toBeNull();
  });

  it("rejects unsafe external return destinations", async () => {
    const { getSafeReturnTo } = await import("../features/auth/utils/return-to");
    expect(getSafeReturnTo("https://attacker.example")).toBeNull();
    expect(getSafeReturnTo("//attacker.example")).toBeNull();
    expect(getSafeReturnTo("/private/reports?page=2")).toBe(
      "/private/reports?page=2",
    );
    expect(getSafeReturnTo("/login")).toBeNull();
  });
});
