// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessOnboardingForm } from "./BusinessOnboardingForm";
import { BusinessProfileForm } from "./BusinessProfileForm";
import { GoogleReviewUrlForm } from "./GoogleReviewUrlForm";

const mocks = vi.hoisted(() => ({
  createBusiness: vi.fn(),
  updateBusiness: vi.fn(),
  updateGoogleReviewUrl: vi.fn(),
  getGoogleReviewQr: vi.fn(),
  refreshBusiness: vi.fn(),
  refreshCurrentUser: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/business/profile",
}));

vi.mock("next/link", async () => {
  const React = await import("react");
  return {
    default: ({
      href,
      children,
      ...props
    }: {
      href: string;
      children: React.ReactNode;
      [key: string]: unknown;
    }) => React.createElement("a", { ...props, href }, children),
  };
});

vi.mock("../../auth/hooks/useAuth", () => ({
  useAuth: () => ({ refreshCurrentUser: mocks.refreshCurrentUser }),
}));

vi.mock("../providers/BusinessProvider", () => ({
  useBusiness: () => ({
    business: profile,
    status: "ready",
    error: null,
    refreshBusiness: mocks.refreshBusiness,
    retryBusinessLoad: vi.fn(),
  }),
}));

vi.mock("../services/business.service", () => ({
  createBusiness: mocks.createBusiness,
  updateBusiness: mocks.updateBusiness,
  updateGoogleReviewUrl: mocks.updateGoogleReviewUrl,
  getGoogleReviewQr: mocks.getGoogleReviewQr,
}));

vi.mock("./BusinessRouteGate", () => ({
  BusinessRouteGate: ({ children }: { children: React.ReactNode }) => children,
}));

const profile = {
  id: "business-id",
  name: "Review Shop",
  category: "Retail",
  address: "1 Main Street",
  timezone: "Asia/Kolkata",
  googleReviewUrl: null as string | null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

function fillBusinessForm() {
  fireEvent.change(screen.getByLabelText("Business name *"), {
    target: { value: "Review Shop" },
  });
  fireEvent.change(screen.getByLabelText("Category *"), {
    target: { value: "Retail" },
  });
  fireEvent.change(screen.getByLabelText("Time zone *"), {
    target: { value: "Asia/Kolkata" },
  });
  fireEvent.change(screen.getByLabelText(/^Business address/), {
    target: { value: "1 Main Street" },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.createBusiness.mockResolvedValue(profile);
  mocks.updateBusiness.mockResolvedValue(profile);
  mocks.updateGoogleReviewUrl.mockResolvedValue({
    ...profile,
    googleReviewUrl: "https://g.page/r/example/review",
  });
  mocks.refreshBusiness.mockResolvedValue(profile);
  mocks.refreshCurrentUser.mockResolvedValue({
    id: "owner-id",
    name: "Review Owner",
    email: "owner@example.com",
    accountStatus: "ACTIVE",
    emailVerified: true,
    hasBusiness: true,
  });
  mocks.getGoogleReviewQr.mockResolvedValue(
    new Blob(["png"], { type: "image/png" }),
  );
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.test");
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

describe("business onboarding form", () => {
  it("validates fields and does not create an incomplete business", async () => {
    render(<BusinessOnboardingForm />);
    fireEvent.click(
      screen.getByRole("button", { name: "Create business profile" }),
    );

    expect(await screen.findByText("Business name is required.")).toBeTruthy();
    expect(screen.getByText("Category is required.")).toBeTruthy();
    expect(screen.getByText("Address is required.")).toBeTruthy();
    expect(mocks.createBusiness).not.toHaveBeenCalled();
  });

  it("creates without owner/business IDs, refreshes state, and navigates to profile", async () => {
    render(<BusinessOnboardingForm />);
    fillBusinessForm();
    fireEvent.click(
      screen.getByRole("button", { name: "Create business profile" }),
    );

    await waitFor(() => expect(mocks.createBusiness).toHaveBeenCalledTimes(1));
    expect(mocks.createBusiness).toHaveBeenCalledWith({
      name: "Review Shop",
      category: "Retail",
      address: "1 Main Street",
      timezone: "Asia/Kolkata",
      googleReviewUrl: null,
    });
    expect(mocks.refreshCurrentUser).toHaveBeenCalledTimes(1);
    expect(mocks.refreshBusiness).toHaveBeenCalledTimes(1);
    expect(mocks.replace).toHaveBeenCalledWith("/dashboard");
  });

  it("does not repeat creation when post-create refresh fails", async () => {
    mocks.refreshCurrentUser
      .mockRejectedValueOnce(new Error("Session refresh failed."))
      .mockResolvedValueOnce({
        id: "owner-id",
        name: "Review Owner",
        email: "owner@example.com",
        accountStatus: "ACTIVE",
        emailVerified: true,
        hasBusiness: true,
      });
    render(<BusinessOnboardingForm />);
    fillBusinessForm();
    fireEvent.click(
      screen.getByRole("button", { name: "Create business profile" }),
    );

    expect(
      await screen.findByText(
        "Your business was created, but its profile could not be refreshed. Retry the refresh to continue.",
      ),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Retry profile refresh" }));

    await waitFor(() =>
      expect(mocks.replace).toHaveBeenCalledWith("/dashboard"),
    );
    expect(mocks.createBusiness).toHaveBeenCalledTimes(1);
  });

  it("prevents duplicate business creation", async () => {
    let finishCreate: ((business: typeof profile) => void) | undefined;
    mocks.createBusiness.mockImplementation(
      () => new Promise((resolve) => (finishCreate = resolve)),
    );
    render(<BusinessOnboardingForm />);
    fillBusinessForm();
    const form = screen
      .getByRole("button", { name: "Create business profile" })
      .closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(mocks.createBusiness).toHaveBeenCalledTimes(1);
    finishCreate?.(profile);
  });

  it("shows a backend duplicate profile error", async () => {
    mocks.createBusiness.mockRejectedValue(
      new ApiError(
        "CONFLICT",
        "A business profile already exists for this account.",
        409,
      ),
    );
    render(<BusinessOnboardingForm />);
    fillBusinessForm();
    fireEvent.click(
      screen.getByRole("button", { name: "Create business profile" }),
    );
    expect((await screen.findByRole("alert")).textContent).toContain(
      "A business profile already exists for this account.",
    );
  });
});

describe("business profile form", () => {
  it("populates fields and refreshes profile state after update", async () => {
    render(<BusinessProfileForm business={profile} />);
    expect((screen.getByLabelText("Business name *") as HTMLInputElement).value).toBe(
      "Review Shop",
    );
    fireEvent.change(screen.getByLabelText(/^Business address/), {
      target: { value: "2 Main Street" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save profile" }));

    await screen.findByText("Business profile updated.");
    expect(mocks.updateBusiness).toHaveBeenCalledWith({
      name: "Review Shop",
      category: "Retail",
      address: "2 Main Street",
      timezone: "Asia/Kolkata",
      googleReviewUrl: null,
    });
    expect(mocks.refreshBusiness).toHaveBeenCalledTimes(1);
  });

  it("preserves server errors from profile updates", async () => {
    mocks.updateBusiness.mockRejectedValue(
      new ApiError("BAD_REQUEST", "A valid IANA timezone is required.", 400),
    );
    render(<BusinessProfileForm business={profile} />);
    fireEvent.click(screen.getByRole("button", { name: "Save profile" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "A valid IANA timezone is required.",
    );
  });
});

describe("Google review URL form", () => {
  it("does not submit invalid review URLs and saves a supported link", async () => {
    render(<GoogleReviewUrlForm />);
    fireEvent.change(screen.getByLabelText("Google review URL *"), {
      target: { value: "https://attacker.example/review" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save review URL" }));
    expect(
      await screen.findByText("Enter a supported HTTPS Google review URL."),
    ).toBeTruthy();
    expect(mocks.updateGoogleReviewUrl).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Google review URL *"), {
      target: { value: "https://g.page/r/example/review" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save review URL" }));
    await screen.findByText("Google review URL updated.");
    expect(mocks.updateGoogleReviewUrl).toHaveBeenCalledWith({
      googleReviewUrl: "https://g.page/r/example/review",
    });
    expect(mocks.refreshBusiness).toHaveBeenCalled();
  });

  it("displays the backend update failure", async () => {
    mocks.updateGoogleReviewUrl.mockRejectedValue(
      new ApiError("BAD_REQUEST", "Google review URL must be a supported HTTPS Google review destination.", 400),
    );
    render(<GoogleReviewUrlForm />);
    fireEvent.change(screen.getByLabelText("Google review URL *"), {
      target: { value: "https://g.page/r/example/review" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save review URL" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Google review URL must be a supported HTTPS Google review destination.",
    );
  });
});
