import type { PageResponse } from "../../../types/api";

export type ReviewDraftStatus = "GENERATED" | "EDITED" | "APPROVED" | "REJECTED";
export type ReviewResponseTone = "PROFESSIONAL" | "FRIENDLY" | "WARM" | "FORMAL";

export interface ReviewResponseDraft {
  id: string;
  googleReviewId: string;
  reviewerDisplayName: string | null;
  reviewRating: number;
  reviewText: string | null;
  reviewDate: string;
  generatedText: string;
  responseText: string;
  status: ReviewDraftStatus;
  tone: ReviewResponseTone;
  generatedAt: string;
  updatedAt: string;
  approvedAt: string | null;
  publishedAt: string | null;
  rejectionReason: string | null;
}

export type ReviewResponseDraftPage = PageResponse<ReviewResponseDraft>;
