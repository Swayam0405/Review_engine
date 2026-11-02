import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type {
  ReviewDraftStatus,
  ReviewResponseDraft,
  ReviewResponseDraftPage,
  ReviewResponseTone,
} from "../types/review-draft.types";

function reviewDraftPath(reviewId: string): string {
  return `/api/google-reviews/${encodeURIComponent(reviewId)}/response-drafts`;
}

export async function listReviewDrafts(
  reviewId: string,
  options: { page?: number; size?: number; signal?: AbortSignal } = {},
): Promise<ReviewResponseDraftPage> {
  const query = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
  });
  const response = await apiRequest<unknown>(
    `${reviewDraftPath(reviewId)}?${query}`,
    { auth: "required", signal: options.signal },
  );
  return parsePage(response);
}

export async function generateReviewDraft(
  reviewId: string,
  tone: ReviewResponseTone,
): Promise<ReviewResponseDraft> {
  const response = await apiRequest<unknown>(reviewDraftPath(reviewId), {
    method: "POST",
    body: { tone },
    auth: "required",
  });
  return parseDraft(response);
}

export async function editReviewDraft(
  draftId: string,
  responseText: string,
): Promise<ReviewResponseDraft> {
  return parseDraft(
    await apiRequest<unknown>(
      `/api/review-response-drafts/${encodeURIComponent(draftId)}`,
      {
        method: "PUT",
        body: { responseText },
        auth: "required",
      },
    ),
  );
}

export async function approveReviewDraft(
  draftId: string,
): Promise<ReviewResponseDraft> {
  return parseDraft(
    await apiRequest<unknown>(
      `/api/review-response-drafts/${encodeURIComponent(draftId)}/approve`,
      { method: "POST", auth: "required" },
    ),
  );
}

export async function rejectReviewDraft(
  draftId: string,
  reason?: string,
): Promise<ReviewResponseDraft> {
  return parseDraft(
    await apiRequest<unknown>(
      `/api/review-response-drafts/${encodeURIComponent(draftId)}/reject`,
      {
        method: "POST",
        ...(reason ? { body: { reason } } : {}),
        auth: "required",
      },
    ),
  );
}

function parsePage(value: unknown): ReviewResponseDraftPage {
  if (
    !isRecord(value) ||
    !Array.isArray(value.content) ||
    !isNumber(value.pageNumber) ||
    !isNumber(value.pageSize) ||
    !isNumber(value.totalElements) ||
    !isNumber(value.totalPages) ||
    typeof value.hasNext !== "boolean" ||
    typeof value.hasPrevious !== "boolean"
  ) {
    throw invalidResponse();
  }
  return {
    content: value.content.map(parseDraft),
    pageNumber: value.pageNumber,
    pageSize: value.pageSize,
    totalElements: value.totalElements,
    totalPages: value.totalPages,
    hasNext: value.hasNext,
    hasPrevious: value.hasPrevious,
  };
}

function parseDraft(value: unknown): ReviewResponseDraft {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.googleReviewId !== "string" ||
    !isNullableString(value.reviewerDisplayName) ||
    !isNumber(value.reviewRating) ||
    !isNullableString(value.reviewText) ||
    typeof value.reviewDate !== "string" ||
    typeof value.generatedText !== "string" ||
    typeof value.responseText !== "string" ||
    !isDraftStatus(value.status) ||
    !isTone(value.tone) ||
    typeof value.generatedAt !== "string" ||
    typeof value.updatedAt !== "string" ||
    !isNullableString(value.approvedAt) ||
    !isNullableString(value.publishedAt) ||
    !isNullableString(value.rejectionReason)
  ) {
    throw invalidResponse();
  }
  return {
    id: value.id,
    googleReviewId: value.googleReviewId,
    reviewerDisplayName: value.reviewerDisplayName,
    reviewRating: value.reviewRating,
    reviewText: value.reviewText,
    reviewDate: value.reviewDate,
    generatedText: value.generatedText,
    responseText: value.responseText,
    status: value.status,
    tone: value.tone,
    generatedAt: value.generatedAt,
    updatedAt: value.updatedAt,
    approvedAt: value.approvedAt,
    publishedAt: value.publishedAt,
    rejectionReason: value.rejectionReason,
  };
}

function isDraftStatus(value: unknown): value is ReviewDraftStatus {
  return (
    value === "GENERATED" ||
    value === "EDITED" ||
    value === "APPROVED" ||
    value === "REJECTED"
  );
}

function isTone(value: unknown): value is ReviewResponseTone {
  return (
    value === "PROFESSIONAL" ||
    value === "FRIENDLY" ||
    value === "WARM" ||
    value === "FORMAL"
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

function isNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function invalidResponse(): ApiError {
  return new ApiError(
    "INVALID_RESPONSE",
    "The server returned invalid response-draft data.",
  );
}
