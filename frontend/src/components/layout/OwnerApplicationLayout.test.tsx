// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { OwnerApplicationLayout } from "./OwnerApplicationLayout";

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  pathname: "/feedback/requests/123",
}));

vi.mock("next/navigation", () => ({
  usePathname: () => mocks.pathname,
}));

vi.mock("next/link", async () => {
  const React = await import("react");
  return {
    default: ({
      href,
      children,
      ...props
    }: { href: string; children: ReactNode; [key: string]: unknown }) =>
      React.createElement("a", { ...props, href }, children),
  };
});

vi.mock("../../features/business/providers/BusinessProvider", () => ({
  BusinessProvider: ({ children }: { children: ReactNode }) => children,
  useBusiness: () => ({ status: "ready" }),
}));

vi.mock("../../features/auth/hooks/useAuth", () => ({
  useAuth: () => ({
    user: { name: "Review Owner" },
    logout: mocks.logout,
  }),
}));

beforeEach(() => {
  vi.clearAllMocks();
  mocks.logout.mockResolvedValue(undefined);
  mocks.pathname = "/feedback/requests/123";
});

afterEach(cleanup);

describe("owner application navigation", () => {
  it("renders working owner links and marks the active section", () => {
    render(
      <OwnerApplicationLayout>
        <p>Owner page</p>
      </OwnerApplicationLayout>,
    );

    expect(screen.getByRole("link", { name: "Dashboard" }).getAttribute("href"))
      .toBe("/dashboard");
    expect(screen.getByRole("link", { name: "Feedback requests" }).getAttribute("aria-current"))
      .toBe("page");
    expect(screen.getByText("Owner page")).toBeTruthy();
  });

  it("calls logout and truthfully reports a failed server logout", async () => {
    mocks.logout.mockRejectedValue(new Error("network detail"));
    render(
      <OwnerApplicationLayout>
        <p>Owner page</p>
      </OwnerApplicationLayout>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(mocks.logout).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole("alert")).toHaveProperty(
      "textContent",
      "The server could not confirm sign-out. The local session was cleared; sign in again to continue.",
    );
    expect(screen.queryByText("network detail")).toBeNull();
  });
});
