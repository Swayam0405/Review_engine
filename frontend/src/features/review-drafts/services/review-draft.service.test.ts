import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "../../../lib/api/api-client";
import { editReviewDraft, generateReviewDraft, listReviewDrafts, approveReviewDraft, rejectReviewDraft } from "./review-draft.service";

vi.mock("../../../lib/api/api-client", () => ({
  apiRequest: vi.fn(),
}));

const draft = {
  id: "draft-1",
  googleReviewId: "review/1",
  reviewerDisplayName: "Reviewer",
  reviewRating: 4,
  reviewText: "Helpful service.",
  reviewDate: "2026-10-28T10:00:00Z",
  generatedText: "Thank you for your feedback.",
  responseText: "Thank you for your feedback.",
  status: "GENERATED",
  tone: "PROFESSIONAL",
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
  vi.resetAllMocks();
  vi.mocked(apiRequest).mockResolvedValue(page);
});

describe("review response draft service", () => {
  it("verifies an imported review through the paginated, authenticated list endpoint", async () => {
    const signal = new AbortController().signal;
    await expect(listReviewDrafts("review/1", { page: 2, size: 15, signal })).resolves.toMatchObject(page);
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/google-reviews/review%2F1/response-drafts?page=2&size=15",
      { auth: "required", signal },
    );
  });

  it("uses the backend draft-generation contract and selected tone", async () => {
    vi.mocked(apiRequest).mockResolvedValue(draft);
    await generateReviewDraft("review-1", "WARM");
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/google-reviews/review-1/response-drafts",
      { method: "POST", body: { tone: "WARM" }, auth: "required" },
    );
  });

  it("edits only responseText and uses actual approval and rejection actions", async () => {
    vi.mocked(apiRequest).mockResolvedValue(draft);
    await editReviewDraft("draft-1", "Updated response");
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/review-response-drafts/draft-1",
      { method: "PUT", body: { responseText: "Updated response" }, auth: "required" },
    );

    await approveReviewDraft("draft-1");
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/review-response-drafts/draft-1/approve",
      { method: "POST", auth: "required" },
    );
    await rejectReviewDraft("draft-1", "Not appropriate");
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/review-response-drafts/draft-1/reject",
      { method: "POST", body: { reason: "Not appropriate" }, auth: "required" },
    );
  });

  it("omits an empty optional rejection reason and rejects malformed backend drafts", async () => {
    vi.mocked(apiRequest).mockResolvedValue(draft);
    await rejectReviewDraft("draft-1");
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/review-response-drafts/draft-1/reject",
      { method: "POST", auth: "required" },
    );
    vi.mocked(apiRequest).mockResolvedValue({ ...draft, status: "PUBLISHED" });
    await expect(generateReviewDraft("review-1", "FRIENDLY")).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });
});
