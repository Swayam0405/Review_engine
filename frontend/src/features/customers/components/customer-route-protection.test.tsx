// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessProvider } from "../../business/providers/BusinessProvider";
import { CustomerListPage } from "./CustomerListPage";

const mocks = vi.hoisted(() => ({
  getMyBusiness: vi.fn(),
  listCustomers: vi.fn(),
  replace: vi.fn(),
  authStatus: "authenticated" as "authenticated" | "unauthenticated",
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/customers",
}));

vi.mock("../../auth/hooks/useAuth", () => ({
  useAuth: () => ({
    status: mocks.authStatus,
    isAuthenticated: mocks.authStatus === "authenticated",
  }),
}));

vi.mock("../../business/services/business.service", () => ({
  getMyBusiness: mocks.getMyBusiness,
}));

vi.mock("../services/customer.service", () => ({
  listCustomers: mocks.listCustomers,
}));

beforeEach(() => {
  vi.clearAllMocks();
  mocks.authStatus = "authenticated";
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("customer route protection", () => {
  it("does not render customer data for an unauthenticated user", async () => {
    mocks.authStatus = "unauthenticated";
    render(
      <BusinessProvider>
        <CustomerListPage />
      </BusinessProvider>,
    );

    expect(await screen.findByText("Redirecting to sign in…")).toBeTruthy();
    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith(
        "/login?returnTo=%2Fcustomers",
      ),
    );
    expect(mocks.getMyBusiness).not.toHaveBeenCalled();
    expect(mocks.listCustomers).not.toHaveBeenCalled();
  });

  it("does not load customer records before a business exists", async () => {
    mocks.getMyBusiness.mockRejectedValue(
      new ApiError("NOT_FOUND", "Business onboarding is required.", 404),
    );
    render(
      <BusinessProvider>
        <CustomerListPage />
      </BusinessProvider>,
    );

    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith(
        "/business/onboarding?returnTo=%2Fcustomers",
      ),
    );
    expect(screen.queryByRole("heading", { name: "Customers" })).toBeNull();
    expect(mocks.listCustomers).not.toHaveBeenCalled();
  });
});
