// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, waitFor } from "@testing-library/react";
import Home from "./page";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  status: "authenticated",
  user: { hasBusiness: true },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
}));

vi.mock("../features/auth/hooks/useAuth", () => ({
  useAuth: () => ({ status: mocks.status, user: mocks.user }),
}));

beforeEach(() => {
  vi.clearAllMocks();
  window.history.replaceState({}, "", "/");
  mocks.status = "authenticated";
  mocks.user = { hasBusiness: true };
});

afterEach(cleanup);

describe("application entry route", () => {
  it("routes an authenticated business owner to the dashboard", async () => {
    render(<Home />);
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/dashboard"));
  });

  it("routes an authenticated user without a business to onboarding", async () => {
    mocks.user = { hasBusiness: false };
    render(<Home />);
    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith("/business/onboarding"),
    );
  });

  it("preserves safe internal destinations for unauthenticated owners", async () => {
    window.history.replaceState({}, "", "/?returnTo=%2Ffeedback%2Frequests%3Fpage%3D2");
    mocks.status = "unauthenticated";
    render(<Home />);
    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith(
        "/login?returnTo=%2Ffeedback%2Frequests%3Fpage%3D2",
      ),
    );
  });
});
