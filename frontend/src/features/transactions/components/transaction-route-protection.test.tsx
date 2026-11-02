// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessProvider } from "../../business/providers/BusinessProvider";
import { TransactionListPage } from "./TransactionListPage";

const mocks = vi.hoisted(() => ({
  getMyBusiness: vi.fn(),
  listTransactions: vi.fn(),
  replace: vi.fn(),
  authStatus: "authenticated" as "authenticated" | "unauthenticated",
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/transactions",
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

vi.mock("../services/transaction.service", () => ({
  listTransactions: mocks.listTransactions,
}));

beforeEach(() => {
  vi.clearAllMocks();
  mocks.authStatus = "authenticated";
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("transaction route protection", () => {
  it("does not render transaction data to unauthenticated users", async () => {
    mocks.authStatus = "unauthenticated";
    render(
      <BusinessProvider>
        <TransactionListPage />
      </BusinessProvider>,
    );
    expect(await screen.findByText("Redirecting to sign in…")).toBeTruthy();
    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith(
        "/login?returnTo=%2Ftransactions",
      ),
    );
    expect(mocks.getMyBusiness).not.toHaveBeenCalled();
    expect(mocks.listTransactions).not.toHaveBeenCalled();
  });

  it("does not request transactions if the authenticated user has no business", async () => {
    mocks.getMyBusiness.mockRejectedValue(
      new ApiError("NOT_FOUND", "Business onboarding is required.", 404),
    );
    render(
      <BusinessProvider>
        <TransactionListPage />
      </BusinessProvider>,
    );
    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith(
        "/business/onboarding?returnTo=%2Ftransactions",
      ),
    );
    expect(screen.queryByRole("heading", { name: "Transactions" })).toBeNull();
    expect(mocks.listTransactions).not.toHaveBeenCalled();
  });
});
