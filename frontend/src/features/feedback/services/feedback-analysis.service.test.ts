import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import {
  getFeedbackAnalysis,
  retryFeedbackAnalysis,
} from "./feedback-analysis.service";

vi.mock("../../../lib/api/api-client", () => ({
  apiRequest: vi.fn(),
}));

const response = {
  feedbackId: "feedback-uuid",
  status: "COMPLETED",
  analysis: {
    sentiment: "MIXED",
    sentimentConfidence: 0.82,
    sentimentSummary: "Customers noted both strengths and areas to improve.",
    topics: [{ name: "Wait time", sentiment: "NEGATIVE", evidence: "Wait was long." }],
    aspects: [
      {
        name: "Staff",
        sentiment: "POSITIVE",
        explanation: "Staff were helpful.",
        evidence: "Very helpful.",
      },
    ],
    issues: [
      {
        name: "Long wait",
        description: "The service took longer than expected.",
        severity: "MEDIUM",
        topic: "Wait time",
        evidence: "Wait was long.",
      },
    ],
    positiveHighlights: ["Helpful staff"],
    negativeHighlights: ["Long wait"],
    improvementOpportunities: ["Consider reducing wait times."],
    futureBackendField: { ignored: true },
  },
  safeError: null,
  analyzedAt: "2026-10-31T12:00:00Z",
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(apiRequest).mockResolvedValue(response);
});

describe("feedback analysis service", () => {
  it("retrieves a validated result through the authenticated analysis endpoint", async () => {
    const signal = new AbortController().signal;
    const result = await getFeedbackAnalysis("feedback-uuid", signal);
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/feedback/feedback-uuid/analysis",
      { auth: "required", signal },
    );
    expect(result).toMatchObject({
      feedbackId: "feedback-uuid",
      status: "COMPLETED",
      analysis: {
        sentiment: "MIXED",
        sentimentConfidence: 0.82,
        topics: [{ name: "Wait time", sentiment: "NEGATIVE" }],
        improvementOpportunities: ["Consider reducing wait times."],
      },
    });
    expect(result.analysis).not.toHaveProperty("futureBackendField");
  });

  it("supports omitted optional analysis fields and unknown future statuses safely", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      feedbackId: "feedback-uuid",
      status: "FUTURE_STATUS",
      analysis: {
        topics: null,
        sentiment: "FUTURE_SENTIMENT",
        sentimentConfidence: 2,
      },
    });
    const result = await getFeedbackAnalysis("feedback-uuid");
    expect(result.status).toBe("FUTURE_STATUS");
    expect(result.safeError).toBeNull();
    expect(result.analyzedAt).toBeNull();
    expect(result.analysis).toMatchObject({
      sentiment: "FUTURE_SENTIMENT",
      sentimentConfidence: null,
      sentimentSummary: null,
      topics: [],
      aspects: [],
      issues: [],
      positiveHighlights: [],
      negativeHighlights: [],
      improvementOpportunities: [],
    });
  });

  it("retries only through the backend retry endpoint with authenticated POST", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce(undefined);
    await retryFeedbackAnalysis("feedback-uuid");
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/feedback/feedback-uuid/analysis/retry",
      { method: "POST", auth: "required" },
    );
  });

  it("preserves normalized API errors", async () => {
    const failure = new ApiError("CONFLICT", "Only failed analysis jobs can be retried.", 409);
    vi.mocked(apiRequest).mockRejectedValueOnce(failure);
    await expect(retryFeedbackAnalysis("feedback-uuid")).rejects.toBe(failure);
  });
});
