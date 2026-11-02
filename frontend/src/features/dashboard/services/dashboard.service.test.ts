import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import { generateDashboardSummary, getDashboard } from "./dashboard.service";

vi.mock("../../../lib/api/api-client", () => ({
  apiRequest: vi.fn(),
}));

const coverage = {
  totalFeedback: 5,
  analyzed: 4,
  pending: 1,
  failed: 0,
  analyzedPercentage: 80,
};
const overview = {
  period: { from: "2026-10-01T00:00:00Z", to: "2026-11-01T00:00:00Z" },
  feedbackMetrics: {
    totalCompletedFeedback: 5,
    averageRating: 4.2,
    positiveFeedback: 2,
    neutralFeedback: 1,
    negativeFeedback: 1,
    mixedFeedback: 0,
  },
  ratingDistribution: { 5: { count: 2, percentage: 40 } },
  sentimentDistribution: { POSITIVE: { count: 2, percentage: 50 } },
  analysisCoverage: coverage,
  comparison: null,
};
const responseByPath: Record<string, unknown> = {
  "/api/dashboard/overview": overview,
  "/api/dashboard/trends?interval=WEEKLY": {
    from: overview.period.from,
    to: overview.period.to,
    interval: "WEEKLY",
    points: [{
      periodStart: "2026-10-26T00:00:00Z",
      periodEnd: "2026-11-01T00:00:00Z",
      feedbackCount: 2,
      averageRating: 4.5,
      sentimentDistribution: { POSITIVE: 1 },
      analysisCoverage: coverage,
    }],
  },
  "/api/dashboard/topics?limit=10": [{
    topic: "Wait time",
    feedbackCount: 2,
    positiveMentions: 0,
    neutralMentions: 0,
    negativeMentions: 2,
    mixedMentions: 0,
    analyzedFeedbackPercentage: 50,
    sentimentDistribution: { NEGATIVE: 2 },
    from: overview.period.from,
    to: overview.period.to,
  }],
  "/api/dashboard/aspects?limit=10": [{
    aspect: "Staff",
    mentions: 2,
    sentimentDistribution: { POSITIVE: 2 },
    averageSentimentConfidence: 0.75,
    from: overview.period.from,
    to: overview.period.to,
  }],
  "/api/dashboard/issues?limit=10": [{
    issue: "Long wait",
    description: "Service took longer than expected.",
    distinctFeedbackReports: 2,
    severityDistribution: { MEDIUM: 2 },
    relatedTopics: "Wait time",
    firstReportedAt: "2026-10-02T00:00:00Z",
    mostRecentlyReportedAt: "2026-10-10T00:00:00Z",
    sentimentDistribution: { NEGATIVE: 2 },
  }],
  "/api/dashboard/highlights?limit=10": {
    from: overview.period.from,
    to: overview.period.to,
    positive: [],
    negative: [],
  },
  "/api/dashboard/summary": {
    id: "summary-1",
    from: overview.period.from,
    to: overview.period.to,
    summary: "A measured summary.",
    positiveObservations: ["Helpful staff"],
    mainConcerns: ["Wait time"],
    recurringIssues: ["Long queue"],
    improvementRecommendations: ["Review scheduling."],
    feedbackCountUsed: 4,
    latestFeedbackAt: "2026-10-30T00:00:00Z",
    generatedAt: "2026-10-31T00:00:00Z",
    analysisCoverage: coverage,
  },
  "/api/dashboard/summary/generate": {
    id: "summary-1",
    from: overview.period.from,
    to: overview.period.to,
    summary: "A measured summary.",
    positiveObservations: ["Helpful staff"],
    mainConcerns: ["Wait time"],
    recurringIssues: ["Long queue"],
    improvementRecommendations: ["Review scheduling."],
    feedbackCountUsed: 4,
    latestFeedbackAt: "2026-10-30T00:00:00Z",
    generatedAt: "2026-10-31T00:00:00Z",
    analysisCoverage: coverage,
  },
  "/api/dashboard/activity": {
    from: overview.period.from,
    to: overview.period.to,
    totalRequests: 8,
    pendingRequests: 1,
    scheduledRequests: 1,
    sentRequests: 1,
    deliveredRequests: 1,
    failedRequests: 0,
    completedRequests: 4,
    expiredRequests: 0,
    conversationsInProgress: 1,
    conversationsCompleted: 4,
    analysisPending: 1,
    analysisFailed: 0,
  },
};

function responseForPath(path: string): unknown {
  const basePath = path.split("?")[0] ?? path;
  const matchingQuery = Object.entries(responseByPath).find(([key]) =>
    key.startsWith(`${basePath}?`),
  );
  return responseByPath[path] ?? responseByPath[basePath] ?? matchingQuery?.[1] ?? [];
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(apiRequest).mockImplementation(async (path) => {
    return responseForPath(path);
  });
});

describe("dashboard service", () => {
  it("requests only actual dashboard endpoints with authenticated access", async () => {
    const signal = new AbortController().signal;
    const data = await getDashboard({}, signal);
    expect(apiRequest).toHaveBeenCalledTimes(8);
    for (const [path, options] of vi.mocked(apiRequest).mock.calls) {
      expect(options).toMatchObject({ auth: "required", signal });
      expect(path).toContain("/api/dashboard/");
    }
    expect(data.overview.feedbackMetrics.totalCompletedFeedback).toBe(5);
    expect(data.trends.points[0]?.feedbackCount).toBe(2);
    expect(data.topics[0]?.topic).toBe("Wait time");
    expect(data.aspects[0]?.aspect).toBe("Staff");
    expect(data.issues[0]?.distinctFeedbackReports).toBe(2);
    expect(data.summary?.improvementRecommendations).toEqual(["Review scheduling."]);
  });

  it("applies the same UTC date range to every supported dashboard request", async () => {
    await getDashboard({
      from: "2026-10-01T00:00:00.000Z",
      to: "2026-11-01T00:00:00.000Z",
    });
    const paths = vi.mocked(apiRequest).mock.calls.map(([path]) => path);
    expect(paths).toContain(
      "/api/dashboard/trends?from=2026-10-01T00%3A00%3A00.000Z&to=2026-11-01T00%3A00%3A00.000Z&interval=WEEKLY",
    );
    expect(paths).toContain(
      "/api/dashboard/topics?from=2026-10-01T00%3A00%3A00.000Z&to=2026-11-01T00%3A00%3A00.000Z&limit=10",
    );
    expect(paths).toContain(
      "/api/dashboard/summary?from=2026-10-01T00%3A00%3A00.000Z&to=2026-11-01T00%3A00%3A00.000Z",
    );
  });

  it("treats a missing saved summary as optional but surfaces other failures", async () => {
    vi.mocked(apiRequest).mockImplementation(async (path) => {
      if (path === "/api/dashboard/summary") {
        throw new ApiError("NOT_FOUND", "Missing.", 404);
      }
      return responseForPath(path);
    });
    expect((await getDashboard()).summary).toBeNull();

    vi.mocked(apiRequest).mockImplementation(async (path) => {
      if (path === "/api/dashboard/overview") {
        throw new ApiError("FORBIDDEN", "Access denied.", 403);
      }
      return responseForPath(path);
    });
    await expect(getDashboard()).rejects.toMatchObject({ status: 403 });
  });

  it("uses the authenticated generate endpoint without bypassing the backend cooldown", async () => {
    const summary = await generateDashboardSummary({
      from: "2026-10-01T00:00:00.000Z",
      to: "2026-11-01T00:00:00.000Z",
    });
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/dashboard/summary/generate?from=2026-10-01T00%3A00%3A00.000Z&to=2026-11-01T00%3A00%3A00.000Z",
      { method: "POST", auth: "required" },
    );
    expect(summary.summary).toBe("A measured summary.");
  });
});
