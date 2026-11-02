// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { FeedbackAnalysisPage } from "./FeedbackAnalysisPage";

const mocks = vi.hoisted(() => ({
  getFeedbackAnalysis: vi.fn(),
  retryFeedbackAnalysis: vi.fn(),
}));

vi.mock("../../business/components/BusinessRouteGate", () => ({
  BusinessRouteGate: ({ children }: { children: ReactNode }) => (
    <div data-testid="business-protected">{children}</div>
  ),
}));

vi.mock("../services/feedback-analysis.service", () => ({
  getFeedbackAnalysis: mocks.getFeedbackAnalysis,
  retryFeedbackAnalysis: mocks.retryFeedbackAnalysis,
}));

const completed = {
  feedbackId: "actual-feedback-uuid",
  status: "COMPLETED",
  safeError: null,
  analyzedAt: "2026-10-31T12:00:00Z",
  analysis: {
    sentiment: "MIXED",
    sentimentConfidence: 0.823,
    sentimentSummary: "Positive service with a wait-time concern.",
    topics: [{ name: "Wait time", sentiment: "NEGATIVE", evidence: "We waited too long." }],
    aspects: [
      {
        name: "Staff",
        sentiment: "POSITIVE",
        explanation: "Staff were helpful.",
        evidence: "The staff member helped us.",
      },
    ],
    issues: [
      {
        name: "Long wait",
        description: "Service took longer than expected.",
        severity: "MEDIUM",
        topic: "Wait time",
        evidence: "We waited too long.",
      },
    ],
    positiveHighlights: ["Helpful staff"],
    negativeHighlights: ["Wait time"],
    improvementOpportunities: ["Consider reducing wait times."],
  },
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.getFeedbackAnalysis.mockResolvedValue(completed);
  mocks.retryFeedbackAnalysis.mockResolvedValue(undefined);
});

afterEach(() => {
  cleanup();
});

describe("individual feedback analysis", () => {
  it("uses the supplied feedback ID behind the business route gate", async () => {
    render(<FeedbackAnalysisPage feedbackId="actual-feedback-uuid" />);
    expect(await screen.findByText("Positive service with a wait-time concern.")).toBeTruthy();
    expect(mocks.getFeedbackAnalysis).toHaveBeenCalledWith(
      "actual-feedback-uuid",
      expect.any(AbortSignal),
    );
    expect(screen.getByTestId("business-protected")).toBeTruthy();
  });

  it("renders the backend sentiment, confidence, topics, aspects, issues, summary and recommendations", async () => {
    render(<FeedbackAnalysisPage feedbackId="actual-feedback-uuid" />);
    await screen.findByText("Positive service with a wait-time concern.");
    expect(screen.getByText("Mixed")).toBeTruthy();
    expect(screen.getByText("82.3%")).toBeTruthy();
    expect(screen.getAllByText("Wait time").length).toBeGreaterThan(0);
    expect(screen.getByText("Staff were helpful.")).toBeTruthy();
    expect(screen.getByText("Service took longer than expected.")).toBeTruthy();
    expect(screen.getByText("Helpful staff")).toBeTruthy();
    expect(screen.getByText("Consider reducing wait times.")).toBeTruthy();
    expect(screen.getByText(/AI-generated interpretations/)).toBeTruthy();
  });

  it.each([
    ["PENDING", "Queued"],
    ["PROCESSING", "Processing"],
  ])("shows an asynchronous %s analysis state", async (status, label) => {
    mocks.getFeedbackAnalysis.mockResolvedValue({
      feedbackId: "actual-feedback-uuid",
      status,
      analysis: null,
      safeError: null,
      analyzedAt: null,
    });
    render(<FeedbackAnalysisPage feedbackId="actual-feedback-uuid" />);
    expect(await screen.findByText(label)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Retry analysis" })).toBeNull();
  });

  it("offers a guarded retry only for failed jobs and refreshes the status", async () => {
    mocks.getFeedbackAnalysis
      .mockResolvedValueOnce({
        feedbackId: "actual-feedback-uuid",
        status: "FAILED",
        analysis: null,
        safeError: "Analysis failed after the configured retry limit.",
        analyzedAt: null,
      })
      .mockResolvedValueOnce({
        feedbackId: "actual-feedback-uuid",
        status: "PENDING",
        analysis: null,
        safeError: null,
        analyzedAt: null,
      });
    let resolveRetry!: () => void;
    mocks.retryFeedbackAnalysis.mockImplementation(
      () => new Promise<void>((resolve) => { resolveRetry = resolve; }),
    );
    render(<FeedbackAnalysisPage feedbackId="actual-feedback-uuid" />);
    const button = await screen.findByRole("button", { name: "Retry analysis" });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(mocks.retryFeedbackAnalysis).toHaveBeenCalledTimes(1);
    expect(mocks.retryFeedbackAnalysis).toHaveBeenCalledWith("actual-feedback-uuid");
    resolveRetry();
    expect(await screen.findByText("Queued")).toBeTruthy();
    expect(mocks.getFeedbackAnalysis).toHaveBeenCalledTimes(2);
  });

  it("shows a safe unavailable state for a missing or unauthorized feedback record", async () => {
    mocks.getFeedbackAnalysis.mockRejectedValue(
      new ApiError("NOT_FOUND", "Not found.", 404),
    );
    render(<FeedbackAnalysisPage feedbackId="unknown-feedback-uuid" />);
    expect(await screen.findByText(/No feedback analysis is available/)).toBeTruthy();
  });

  it("renders empty sections and does not invent an unknown sentiment", async () => {
    mocks.getFeedbackAnalysis.mockResolvedValue({
      feedbackId: "actual-feedback-uuid",
      status: "COMPLETED",
      analysis: {
        sentiment: null,
        sentimentConfidence: null,
        sentimentSummary: null,
        topics: [],
        aspects: [],
        issues: [],
        positiveHighlights: [],
        negativeHighlights: [],
        improvementOpportunities: [],
      },
      safeError: null,
      analyzedAt: null,
    });
    render(<FeedbackAnalysisPage feedbackId="actual-feedback-uuid" />);
    expect(await screen.findByText("Not provided")).toBeTruthy();
    expect(screen.getAllByText(/No (topics|aspects|issues|items) were returned/).length).toBeGreaterThan(0);
    expect(screen.queryByText("0%")).toBeNull();
  });

  it("does not claim completion for a completed job without a payload or unknown status", async () => {
    mocks.getFeedbackAnalysis.mockResolvedValue({
      feedbackId: "actual-feedback-uuid",
      status: "PROCESSED",
      analysis: null,
      safeError: null,
      analyzedAt: null,
    });
    render(<FeedbackAnalysisPage feedbackId="actual-feedback-uuid" />);
    expect(await screen.findByText(/unrecognized status or a processed job/)).toBeTruthy();
    expect(screen.queryByText("Individual feedback analysis")).toBeNull();
  });

  it("refreshes status on demand without automatic polling", async () => {
    render(<FeedbackAnalysisPage feedbackId="actual-feedback-uuid" />);
    await screen.findByText("Positive service with a wait-time concern.");
    fireEvent.click(screen.getByRole("button", { name: "Refresh status" }));
    await waitFor(() => expect(mocks.getFeedbackAnalysis).toHaveBeenCalledTimes(2));
  });
});
