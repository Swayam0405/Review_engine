// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import type { ReactNode } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessProvider } from "../providers/BusinessProvider";
import { BusinessRouteGate } from "./BusinessRouteGate";

const mocks = vi.hoisted(() => ({
  getMyBusiness: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/business/profile",
}));

vi.mock("../../auth/hooks/useAuth", () => ({
  useAuth: () => ({ status: "authenticated" }),
}));

vi.mock("../../auth/components/ProtectedRoute", () => ({
  ProtectedRoute: ({ children }: { children: ReactNode }) => children,
}));

vi.mock("../services/business.service", () => ({
  getMyBusiness: mocks.getMyBusiness,
}));

const profile = {
  id: "business-id",
  name: "Review Shop",
  category: "Retail",
  address: "1 Main Street",
  timezone: "Asia/Kolkata",
  googleReviewUrl: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("business onboarding route gate", () => {
  it("allows authenticated users without a business to see onboarding", async () => {
    mocks.getMyBusiness.mockRejectedValue(
      new ApiError("NOT_FOUND", "Business onboarding is required.", 404),
    );
    render(
      <BusinessProvider>
        <BusinessRouteGate onboarding>
          <p>Business onboarding form</p>
        </BusinessRouteGate>
      </BusinessProvider>,
    );

    expect(screen.getByRole("status").textContent).toBe("Loading your business…");
    expect(await screen.findByText("Business onboarding form")).toBeTruthy();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("redirects users who already have a business away from onboarding", async () => {
    mocks.getMyBusiness.mockResolvedValue(profile);
    render(
      <BusinessProvider>
        <BusinessRouteGate onboarding>
          <p>Business onboarding form</p>
        </BusinessRouteGate>
      </BusinessProvider>,
    );

    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith("/business/profile"),
    );
    expect(screen.queryByText("Business onboarding form")).toBeNull();
  });

  it("surfaces non-404 retrieval failures instead of assuming no business", async () => {
    mocks.getMyBusiness.mockRejectedValue(
      new ApiError("NETWORK_ERROR", "Unable to reach the server.", 503),
    );
    render(
      <BusinessProvider>
        <BusinessRouteGate onboarding>
          <p>Business onboarding form</p>
        </BusinessRouteGate>
      </BusinessProvider>,
    );

    expect((await screen.findByRole("alert")).textContent).toContain(
      "Unable to reach the server.",
    );
    expect(screen.queryByText("Business onboarding form")).toBeNull();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("redirects an existing-business route to onboarding only after confirmed 404", async () => {
    mocks.getMyBusiness.mockRejectedValue(
      new ApiError("NOT_FOUND", "Business onboarding is required.", 404),
    );
    render(
      <BusinessProvider>
        <BusinessRouteGate>
          <p>Business profile</p>
        </BusinessRouteGate>
      </BusinessProvider>,
    );

    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith(
        "/business/onboarding?returnTo=%2Fbusiness%2Fprofile",
      ),
    );
    expect(screen.queryByText("Business profile")).toBeNull();
  });

  it("aborts pending profile retrieval when the route unmounts", async () => {
    let receivedSignal: AbortSignal | undefined;
    mocks.getMyBusiness.mockImplementation(
      (signal: AbortSignal) =>
        new Promise((_resolve, reject) => {
          receivedSignal = signal;
          signal.addEventListener("abort", () =>
            reject(new DOMException("Aborted", "AbortError")),
          );
        }),
    );
    const { unmount } = render(
      <BusinessProvider>
        <BusinessRouteGate onboarding>
          <p>Business onboarding form</p>
        </BusinessRouteGate>
      </BusinessProvider>,
    );
    await waitFor(() => expect(receivedSignal).toBeDefined());
    await act(async () => unmount());
    expect(receivedSignal?.aborted).toBe(true);
  });
});
