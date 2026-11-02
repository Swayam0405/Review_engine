// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { DashboardPage } from "./DashboardPage";

const mocks = vi.hoisted(() => ({
  getDashboard: vi.fn(),
  generateDashboardSummary: vi.fn(),
}));

vi.mock("../../business/components/BusinessRouteGate", () => ({
  BusinessRouteGate: ({ children }: { children: ReactNode }) => (
    <div data-testid="business-protected">{children}</div>
  ),
}));

vi.mock("../services/dashboard.service", () => ({
  getDashboard: mocks.getDashboard,
  generateDashboardSummary: mocks.generateDashboardSummary,
}));

const data = {
  overview: {
    period: { from: "2026-10-01T00:00:00Z", to: "2026-11-01T00:00:00Z" },
    feedbackMetrics: {
      totalCompletedFeedback: 4,
      averageRating: 4.25,
      positiveFeedback: 2,
      neutralFeedback: 1,
      negativeFeedback: 1,
      mixedFeedback: 0,
    },
    ratingDistribution: { "5": { count: 2, percentage: 50 } },
    sentimentDistribution: { POSITIVE: { count: 2, percentage: 50 } },
    analysisCoverage: {
      totalFeedback: 4,
      analyzed: 3,
      pending: 1,
      failed: 0,
      analyzedPercentage: 75,
    },
    comparison: {
      previousFrom: "2026-09-01T00:00:00Z",
      previousTo: "2026-10-01T00:00:00Z",
      previousFeedbackCount: 2,
      currentFeedbackCount: 4,
      percentageChange: 100,
    },
  },
  trends: {
    from: "2026-10-01T00:00:00Z",
    to: "2026-11-01T00:00:00Z",
    interval: "WEEKLY" as const,
    points: [{
      periodStart: "2026-10-26T00:00:00Z",
      periodEnd: "2026-11-01T00:00:00Z",
      feedbackCount: 2,
      averageRating: 4.5,
      sentimentDistribution: { POSITIVE: 1 },
      analysisCoverage: {
        totalFeedback: 2,
        analyzed: 1,
        pending: 1,
        failed: 0,
        analyzedPercentage: 50,
      },
    }],
  },
  topics: [{
    topic: "Wait time",
    feedbackCount: 2,
    positiveMentions: 0,
    neutralMentions: 0,
    negativeMentions: 2,
    mixedMentions: 0,
    analyzedFeedbackPercentage: 66.7,
    sentimentDistribution: { NEGATIVE: 2 },
    from: "2026-10-01T00:00:00Z",
    to: "2026-11-01T00:00:00Z",
  }],
  aspects: [{
    aspect: "Staff",
    mentions: 3,
    sentimentDistribution: { POSITIVE: 2, NEUTRAL: 1 },
    averageSentimentConfidence: 0.8,
    from: "2026-10-01T00:00:00Z",
    to: "2026-11-01T00:00:00Z",
  }],
  issues: [{
    issue: "Long wait",
    description: "Service took longer than expected.",
    distinctFeedbackReports: 2,
    severityDistribution: { MEDIUM: 2 },
    relatedTopics: "Wait time",
    firstReportedAt: "2026-10-02T00:00:00Z",
    mostRecentlyReportedAt: "2026-10-10T00:00:00Z",
    sentimentDistribution: { NEGATIVE: 2 },
  }],
  highlights: {
    from: "2026-10-01T00:00:00Z",
    to: "2026-11-01T00:00:00Z",
    positive: [{
      topicOrAspect: "Staff",
      explanation: "Customers appreciated helpful staff.",
      supportingFeedbackRecords: 2,
      sentiment: "POSITIVE",
    }],
    negative: [],
  },
  summary: {
    id: "summary-1",
    from: "2026-10-01T00:00:00Z",
    to: "2026-11-01T00:00:00Z",
    summary: "Customers value staff support and mention wait times.",
    positiveObservations: ["Helpful staff"],
    mainConcerns: ["Long waits"],
    recurringIssues: ["Queue duration"],
    improvementRecommendations: ["Review scheduling."],
    feedbackCountUsed: 3,
    latestFeedbackAt: "2026-10-30T00:00:00Z",
    generatedAt: "2026-10-31T00:00:00Z",
    analysisCoverage: {
      totalFeedback: 4,
      analyzed: 3,
      pending: 1,
      failed: 0,
      analyzedPercentage: 75,
    },
  },
  activity: {
    from: "2026-10-01T00:00:00Z",
    to: "2026-11-01T00:00:00Z",
    totalRequests: 5,
    pendingRequests: 1,
    scheduledRequests: 0,
    sentRequests: 1,
    deliveredRequests: 1,
    failedRequests: 0,
    completedRequests: 2,
    expiredRequests: 0,
    conversationsInProgress: 1,
    conversationsCompleted: 2,
    analysisPending: 1,
    analysisFailed: 0,
  },
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.getDashboard.mockResolvedValue(data);
  mocks.generateDashboardSummary.mockResolvedValue(data.summary);
});

afterEach(cleanup);

describe("business dashboard", () => {
  it("renders returned metrics, insights, trends, summary and activity", async () => {
    render(<DashboardPage />);
    expect(await screen.findByText("Customers value staff support and mention wait times.")).toBeTruthy();
    expect(screen.getByText("4.3 / 5")).toBeTruthy();
    expect(screen.getByText("75.0% of feedback")).toBeTruthy();
    expect(screen.getByText("Weekly UTC buckets; bars show completed feedback count. Counts and average ratings are returned by the backend.")).toBeTruthy();
    expect(screen.getByText("Wait time")).toBeTruthy();
    expect(screen.getByText("Long wait")).toBeTruthy();
    expect(screen.getByText("Helpful staff")).toBeTruthy();
    expect(screen.getAllByText("Analysis pending")).toHaveLength(2);
    expect(screen.getByTestId("business-protected")).toBeTruthy();
  });

  it("renders zero values and avoids an average rating for no feedback", async () => {
    mocks.getDashboard.mockResolvedValue({
      ...data,
      overview: {
        ...data.overview,
        feedbackMetrics: {
          ...data.overview.feedbackMetrics,
          totalCompletedFeedback: 0,
          averageRating: 0,
        },
        analysisCoverage: {
          ...data.overview.analysisCoverage,
          totalFeedback: 0,
          analyzed: 0,
          pending: 0,
          failed: 0,
          analyzedPercentage: 0,
        },
      },
      trends: { ...data.trends, points: [] },
      topics: [],
      aspects: [],
      issues: [],
      highlights: { ...data.highlights, positive: [], negative: [] },
      summary: null,
    });
    render(<DashboardPage />);
    expect(await screen.findByText("No feedback in this period")).toBeTruthy();
    expect(screen.getByText("No trend data for this period.")).toBeTruthy();
    expect(screen.getByText(/No saved summary is available/)).toBeTruthy();
    expect(screen.getAllByText(/No analyzed/).length).toBeGreaterThan(0);
  });

  it("validates and applies a UTC date range to the backend", async () => {
    render(<DashboardPage />);
    await screen.findByText("Customers value staff support and mention wait times.");
    fireEvent.change(screen.getByLabelText("From (UTC)"), {
      target: { value: "2026-10-01" },
    });
    fireEvent.change(screen.getByLabelText("Through (UTC)"), {
      target: { value: "2026-10-31" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Apply date range" }));
    await waitFor(() =>
      expect(mocks.getDashboard).toHaveBeenLastCalledWith({
        from: "2026-10-01T00:00:00.000Z",
        to: "2026-11-01T00:00:00.000Z",
      }, expect.any(AbortSignal)),
    );
  });

  it("offers a retry after dashboard failure and avoids generated fake metrics", async () => {
    mocks.getDashboard
      .mockRejectedValueOnce(new ApiError("NETWORK_ERROR", "Offline."))
      .mockResolvedValueOnce(data);
    render(<DashboardPage />);
    expect(await screen.findByText("Offline.")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Retry dashboard" }));
    expect(await screen.findByText("Customers value staff support and mention wait times.")).toBeTruthy();
  });

  it("generates and renders the summary returned by the backend", async () => {
    const generatedSummary = {
      ...data.summary,
      summary: "Fresh backend summary.",
      generatedAt: "2026-11-01T00:00:00Z",
    };
    mocks.generateDashboardSummary.mockResolvedValue(generatedSummary);
    render(<DashboardPage />);
    await screen.findByText("Customers value staff support and mention wait times.");

    fireEvent.click(screen.getByRole("button", { name: "Refresh summary" }));

    await waitFor(() =>
      expect(mocks.generateDashboardSummary).toHaveBeenCalledWith({}),
    );
    expect(await screen.findByText("Fresh backend summary.")).toBeTruthy();
    expect(screen.getByRole("status").textContent).toContain("up to date");
  });

  it("does not allow summary generation before three analyses are available", async () => {
    mocks.getDashboard.mockResolvedValue({
      ...data,
      overview: {
        ...data.overview,
        analysisCoverage: { ...data.overview.analysisCoverage, analyzed: 2 },
      },
    });
    render(<DashboardPage />);
    await screen.findByText("Customers value staff support and mention wait times.");
    expect(screen.getByRole("button", { name: "Refresh summary" })).toHaveProperty(
      "disabled",
      true,
    );
    expect(mocks.generateDashboardSummary).not.toHaveBeenCalled();
  });
});
