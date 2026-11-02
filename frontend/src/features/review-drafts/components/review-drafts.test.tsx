// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { ReviewDraftsPage } from "./ReviewDraftsPage";

const mocks = vi.hoisted(() => ({
  approveReviewDraft: vi.fn(),
  editReviewDraft: vi.fn(),
  generateReviewDraft: vi.fn(),
  listReviewDrafts: vi.fn(),
  rejectReviewDraft: vi.fn(),
}));

vi.mock("../../business/components/BusinessRouteGate", () => ({
  BusinessRouteGate: ({ children }: { children: ReactNode }) => (
    <div data-testid="business-protected">{children}</div>
  ),
}));

vi.mock("../services/review-draft.service", () => mocks);

const draft = {
  id: "draft-1",
  googleReviewId: "review-1",
  reviewerDisplayName: "A reviewer",
  reviewRating: 4,
  reviewText: "Great service.",
  reviewDate: "2026-10-28T10:00:00Z",
  generatedText: "Thank you for visiting.",
  responseText: "Thank you for visiting.",
  status: "GENERATED" as const,
  tone: "PROFESSIONAL" as const,
  generatedAt: "2026-10-29T10:00:00Z",
  updatedAt: "2026-10-29T10:00:00Z",
  approvedAt: null,
  publishedAt: null,
  rejectionReason: null,
};
const page = {
  content: [draft],
  pageNumber: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.listReviewDrafts.mockResolvedValue(page);
  mocks.generateReviewDraft.mockResolvedValue(draft);
  mocks.editReviewDraft.mockResolvedValue({ ...draft, responseText: "Updated response.", status: "EDITED" });
  mocks.approveReviewDraft.mockResolvedValue({ ...draft, status: "APPROVED" });
  mocks.rejectReviewDraft.mockResolvedValue({ ...draft, status: "REJECTED" });
});

afterEach(cleanup);

describe("review response draft workflow", () => {
  it("verifies the supplied review before enabling draft actions and explains the empty state", async () => {
    mocks.listReviewDrafts.mockResolvedValue({ ...page, content: [] });
    render(<ReviewDraftsPage reviewId="known-review-id" />);
    expect(await screen.findByText("No response drafts yet")).toBeTruthy();
    expect(mocks.listReviewDrafts).toHaveBeenCalledWith("known-review-id", {
      page: 0,
      size: 20,
      signal: expect.any(AbortSignal),
    });
    expect(screen.getByRole("button", { name: "Generate response draft" })).toBeTruthy();
    expect(screen.getByText("Approval records the owner's decision only. No Google publishing API is available, and this frontend never claims an approved response was published.")).toBeTruthy();
    expect(screen.getByTestId("business-protected")).toBeTruthy();
  });

  it("keeps actions hidden when review validation fails and offers a retry", async () => {
    mocks.listReviewDrafts.mockRejectedValueOnce(new Error("Review is unavailable."));
    render(<ReviewDraftsPage reviewId="unknown-id" />);
    expect(await screen.findByText(
      "Drafts could not be loaded for this imported review.",
    )).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Generate response draft" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Retry loading" }));
    expect(await screen.findByRole("button", { name: "Generate response draft" })).toBeTruthy();
  });

  it("generates a draft once, refreshes the list, and renders actual imported-review data", async () => {
    let finish!: (value: typeof draft) => void;
    mocks.generateReviewDraft.mockImplementation(
      () => new Promise((resolve) => { finish = resolve; }),
    );
    render(<ReviewDraftsPage reviewId="known-review-id" />);
    fireEvent.click(await screen.findByRole("button", { name: "Generate response draft" }));
    const generatingButton = screen.getByRole("button", { name: "Generating draft…" });
    fireEvent.click(generatingButton);
    expect(mocks.generateReviewDraft).toHaveBeenCalledTimes(1);
    expect(mocks.generateReviewDraft).toHaveBeenCalledWith("known-review-id", "PROFESSIONAL");
    finish(draft);
    await waitFor(() => expect(mocks.listReviewDrafts).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("A reviewer")).toBeTruthy();
    expect(screen.getByText("Great service.")).toBeTruthy();
    expect(screen.queryByText(/has not been published to Google/)).toBeNull();
  });

  it("edits, approves, and rejects through distinct backend actions without claiming publication", async () => {
    render(<ReviewDraftsPage reviewId="known-review-id" />);
    await screen.findByText("A reviewer");
    fireEvent.change(screen.getByLabelText("Response text"), {
      target: { value: "Thanks for your visit." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save edit" }));
    await waitFor(() => expect(mocks.editReviewDraft).toHaveBeenCalledWith(
      "draft-1",
      "Thanks for your visit.",
    ));
    await screen.findByText("A reviewer");

    fireEvent.click(screen.getByRole("button", { name: "Approve draft" }));
    await waitFor(() => expect(mocks.approveReviewDraft).toHaveBeenCalledWith("draft-1"));
    await screen.findByText("A reviewer");

    fireEvent.change(screen.getByLabelText("Rejection reason (optional)"), {
      target: { value: "Not ready" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Reject draft" }));
    await waitFor(() => expect(mocks.rejectReviewDraft).toHaveBeenCalledWith(
      "draft-1",
      "Not ready",
    ));
    expect(screen.getByText(/No Google publishing API is available/)).toBeTruthy();
  });

  it("does not automatically repeat an ambiguous generation request", async () => {
    mocks.generateReviewDraft.mockRejectedValue(new Error("Network unavailable."));
    render(<ReviewDraftsPage reviewId="known-review-id" />);
    fireEvent.click(await screen.findByRole("button", { name: "Generate response draft" }));
    expect(await screen.findByText(/may have created a draft/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Generation status unknown" })).toBeTruthy();
    expect(mocks.generateReviewDraft).toHaveBeenCalledTimes(1);
  });
});
