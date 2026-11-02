// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { ApiError } from "../../../lib/api/api-error";
import { GoogleReviewQr } from "./GoogleReviewQr";

const mocks = vi.hoisted(() => ({
  getGoogleReviewQr: vi.fn(),
}));

vi.mock("../services/business.service", () => ({
  getGoogleReviewQr: mocks.getGoogleReviewQr,
}));

beforeEach(() => {
  vi.clearAllMocks();
  const NativeURL = URL;
  class MockURL extends NativeURL {
    static createObjectURL = vi.fn(() => "blob:business-review-qr");
    static revokeObjectURL = vi.fn();
  }
  vi.stubGlobal("URL", MockURL);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("Google review QR", () => {
  it("shows loading state, displays backend Blob, offers download and releases URL", async () => {
    let finishLoad: ((blob: Blob) => void) | undefined;
    mocks.getGoogleReviewQr.mockImplementation(
      () =>
        new Promise((resolve) => {
          finishLoad = resolve;
        }),
    );
    const { unmount } = render(<GoogleReviewQr configured />);
    expect(screen.getByRole("status").textContent).toBe("Loading your QR code…");

    await act(async () => {
      finishLoad?.(new Blob(["actual-png"], { type: "image/png" }));
    });
    const image = await screen.findByRole("img", {
      name: "QR code for the configured Google review link",
    });
    expect(image.getAttribute("src")).toBe("blob:business-review-qr");
    expect(screen.getByRole("link", { name: "Download QR code" }).getAttribute("download"))
      .toBe("google-review-qr.png");

    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:business-review-qr");
  });

  it("shows an unconfigured state without making an API request", () => {
    render(<GoogleReviewQr configured={false} />);
    expect(
      screen.getByText("Configure a Google review URL to make its QR code available."),
    ).toBeTruthy();
    expect(mocks.getGoogleReviewQr).not.toHaveBeenCalled();
  });

  it("shows QR loading errors and retries", async () => {
    mocks.getGoogleReviewQr
      .mockRejectedValueOnce(
        new ApiError("NOT_FOUND", "A Google review URL has not been configured.", 404),
      )
      .mockResolvedValueOnce(new Blob(["actual-png"], { type: "image/png" }));
    render(<GoogleReviewQr configured />);

    expect((await screen.findByRole("alert")).textContent).toContain(
      "A Google review URL has not been configured.",
    );
    screen.getByRole("button", { name: "Retry QR code" }).click();
    await waitFor(() => expect(mocks.getGoogleReviewQr).toHaveBeenCalledTimes(2));
    expect(
      await screen.findByRole("img", {
        name: "QR code for the configured Google review link",
      }),
    ).toBeTruthy();
  });
});
