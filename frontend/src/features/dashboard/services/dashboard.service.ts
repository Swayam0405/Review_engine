import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type {
  AnalysisCoverage,
  DashboardActivity,
  DashboardAspect,
  DashboardData,
  DashboardDateRange,
  DashboardHighlight,
  DashboardHighlights,
  DashboardIssue,
  DashboardMetric,
  DashboardOverview,
  DashboardPeriod,
  DashboardSummary,
  DashboardTopic,
  DashboardTrendPoint,
  DashboardTrends,
} from "../types/dashboard.types";

const DASHBOARD_PATH = "/api/dashboard";

export async function getDashboard(
  range: DashboardDateRange = {},
  signal?: AbortSignal,
): Promise<DashboardData> {
  const query = dateQuery(range);
  const options = { auth: "required" as const, signal };
  const [
    overview,
    trends,
    topics,
    aspects,
    issues,
    highlights,
    summary,
    activity,
  ] = await Promise.all([
    apiRequest<unknown>(`${DASHBOARD_PATH}/overview${query}`, options),
    apiRequest<unknown>(
      `${DASHBOARD_PATH}/trends${query}${query ? "&" : "?"}interval=WEEKLY`,
      options,
    ),
    apiRequest<unknown>(
      `${DASHBOARD_PATH}/topics${query}${query ? "&" : "?"}limit=10`,
      options,
    ),
    apiRequest<unknown>(
      `${DASHBOARD_PATH}/aspects${query}${query ? "&" : "?"}limit=10`,
      options,
    ),
    apiRequest<unknown>(
      `${DASHBOARD_PATH}/issues${query}${query ? "&" : "?"}limit=10`,
      options,
    ),
    apiRequest<unknown>(
      `${DASHBOARD_PATH}/highlights${query}${query ? "&" : "?"}limit=10`,
      options,
    ),
    getOptionalSummary(query, options),
    apiRequest<unknown>(`${DASHBOARD_PATH}/activity${query}`, options),
  ]);
  return {
    overview: parseOverview(overview),
    trends: parseTrends(trends),
    topics: parseArray(topics, parseTopic),
    aspects: parseArray(aspects, parseAspect),
    issues: parseArray(issues, parseIssue),
    highlights: parseHighlights(highlights),
    summary: summary === null ? null : parseSummary(summary),
    activity: parseActivity(activity),
  };
}

export async function generateDashboardSummary(
  range: DashboardDateRange = {},
): Promise<DashboardSummary> {
  const summary = await apiRequest<unknown>(
    `${DASHBOARD_PATH}/summary/generate${dateQuery(range)}`,
    { method: "POST", auth: "required" },
  );
  return parseSummary(summary);
}

function dateQuery(range: DashboardDateRange): string {
  const query = new URLSearchParams();
  if (range.from) query.set("from", range.from);
  if (range.to) query.set("to", range.to);
  const serialized = query.toString();
  return serialized ? `?${serialized}` : "";
}

async function getOptionalSummary(
  query: string,
  options: { auth: "required"; signal?: AbortSignal },
): Promise<unknown | null> {
  try {
    return await apiRequest<unknown>(`${DASHBOARD_PATH}/summary${query}`, options);
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 404) return null;
    throw cause;
  }
}

function parseOverview(value: unknown): DashboardOverview {
  if (
    !isRecord(value) ||
    !isRecord(value.period) ||
    !isRecord(value.feedbackMetrics) ||
    !isRecord(value.ratingDistribution) ||
    !isRecord(value.sentimentDistribution) ||
    !isRecord(value.analysisCoverage)
  ) {
    throw invalidResponse();
  }
  const metrics = value.feedbackMetrics;
  return {
    period: parsePeriod(value.period),
    feedbackMetrics: {
      totalCompletedFeedback: numberField(metrics, "totalCompletedFeedback"),
      averageRating: numberField(metrics, "averageRating"),
      positiveFeedback: numberField(metrics, "positiveFeedback"),
      neutralFeedback: numberField(metrics, "neutralFeedback"),
      negativeFeedback: numberField(metrics, "negativeFeedback"),
      mixedFeedback: numberField(metrics, "mixedFeedback"),
    },
    ratingDistribution: parseMetricMap(value.ratingDistribution),
    sentimentDistribution: parseMetricMap(value.sentimentDistribution),
    analysisCoverage: parseCoverage(value.analysisCoverage),
    comparison: value.comparison === null ? null : parseComparison(value.comparison),
  };
}

function parseTrends(value: unknown): DashboardTrends {
  if (
    !isRecord(value) ||
    typeof value.from !== "string" ||
    typeof value.to !== "string" ||
    (value.interval !== "DAILY" &&
      value.interval !== "WEEKLY" &&
      value.interval !== "MONTHLY") ||
    !Array.isArray(value.points)
  ) {
    throw invalidResponse();
  }
  return {
    from: value.from,
    to: value.to,
    interval: value.interval,
    points: value.points.map(parseTrendPoint),
  };
}

function parseTrendPoint(value: unknown): DashboardTrendPoint {
  if (!isRecord(value) || !isRecord(value.analysisCoverage)) {
    throw invalidResponse();
  }
  return {
    periodStart: stringField(value, "periodStart"),
    periodEnd: stringField(value, "periodEnd"),
    feedbackCount: numberField(value, "feedbackCount"),
    averageRating: numberField(value, "averageRating"),
    sentimentDistribution: parseNumberMap(value.sentimentDistribution),
    analysisCoverage: parseCoverage(value.analysisCoverage),
  };
}

function parseTopic(value: unknown): DashboardTopic {
  if (!isRecord(value)) throw invalidResponse();
  return {
    topic: stringField(value, "topic"),
    feedbackCount: numberField(value, "feedbackCount"),
    positiveMentions: numberField(value, "positiveMentions"),
    neutralMentions: numberField(value, "neutralMentions"),
    negativeMentions: numberField(value, "negativeMentions"),
    mixedMentions: numberField(value, "mixedMentions"),
    analyzedFeedbackPercentage: numberField(value, "analyzedFeedbackPercentage"),
    sentimentDistribution: parseNumberMap(value.sentimentDistribution),
    from: stringField(value, "from"),
    to: stringField(value, "to"),
  };
}

function parseAspect(value: unknown): DashboardAspect {
  if (!isRecord(value)) throw invalidResponse();
  return {
    aspect: stringField(value, "aspect"),
    mentions: numberField(value, "mentions"),
    sentimentDistribution: parseNumberMap(value.sentimentDistribution),
    averageSentimentConfidence: numberField(value, "averageSentimentConfidence"),
    from: stringField(value, "from"),
    to: stringField(value, "to"),
  };
}

function parseIssue(value: unknown): DashboardIssue {
  if (!isRecord(value)) throw invalidResponse();
  return {
    issue: stringField(value, "issue"),
    description: stringField(value, "description"),
    distinctFeedbackReports: numberField(value, "distinctFeedbackReports"),
    severityDistribution: parseNumberMap(value.severityDistribution),
    relatedTopics: stringField(value, "relatedTopics"),
    firstReportedAt: stringField(value, "firstReportedAt"),
    mostRecentlyReportedAt: stringField(value, "mostRecentlyReportedAt"),
    sentimentDistribution: parseNumberMap(value.sentimentDistribution),
  };
}

function parseHighlights(value: unknown): DashboardHighlights {
  if (
    !isRecord(value) ||
    typeof value.from !== "string" ||
    typeof value.to !== "string" ||
    !Array.isArray(value.positive) ||
    !Array.isArray(value.negative)
  ) {
    throw invalidResponse();
  }
  return {
    from: value.from,
    to: value.to,
    positive: value.positive.map(parseHighlight),
    negative: value.negative.map(parseHighlight),
  };
}

function parseHighlight(value: unknown): DashboardHighlight {
  if (!isRecord(value)) throw invalidResponse();
  return {
    topicOrAspect: stringField(value, "topicOrAspect"),
    explanation: stringField(value, "explanation"),
    supportingFeedbackRecords: numberField(value, "supportingFeedbackRecords"),
    sentiment: stringField(value, "sentiment"),
  };
}

function parseSummary(value: unknown): DashboardSummary {
  if (
    !isRecord(value) ||
    !isRecord(value.analysisCoverage) ||
    !isNullableString(value.latestFeedbackAt)
  ) {
    throw invalidResponse();
  }
  return {
    id: stringField(value, "id"),
    from: stringField(value, "from"),
    to: stringField(value, "to"),
    summary: stringField(value, "summary"),
    positiveObservations: parseTextCollection(value.positiveObservations),
    mainConcerns: parseTextCollection(value.mainConcerns),
    recurringIssues: parseTextCollection(value.recurringIssues),
    improvementRecommendations: parseTextCollection(value.improvementRecommendations),
    feedbackCountUsed: numberField(value, "feedbackCountUsed"),
    latestFeedbackAt: value.latestFeedbackAt,
    generatedAt: stringField(value, "generatedAt"),
    analysisCoverage: parseCoverage(value.analysisCoverage),
  };
}

function parseActivity(value: unknown): DashboardActivity {
  if (!isRecord(value)) throw invalidResponse();
  return {
    from: stringField(value, "from"),
    to: stringField(value, "to"),
    totalRequests: numberField(value, "totalRequests"),
    pendingRequests: numberField(value, "pendingRequests"),
    scheduledRequests: numberField(value, "scheduledRequests"),
    sentRequests: numberField(value, "sentRequests"),
    deliveredRequests: numberField(value, "deliveredRequests"),
    failedRequests: numberField(value, "failedRequests"),
    completedRequests: numberField(value, "completedRequests"),
    expiredRequests: numberField(value, "expiredRequests"),
    conversationsInProgress: numberField(value, "conversationsInProgress"),
    conversationsCompleted: numberField(value, "conversationsCompleted"),
    analysisPending: numberField(value, "analysisPending"),
    analysisFailed: numberField(value, "analysisFailed"),
  };
}

function parseCoverage(value: unknown): AnalysisCoverage {
  if (!isRecord(value)) throw invalidResponse();
  return {
    totalFeedback: numberField(value, "totalFeedback"),
    analyzed: numberField(value, "analyzed"),
    pending: numberField(value, "pending"),
    failed: numberField(value, "failed"),
    analyzedPercentage: numberField(value, "analyzedPercentage"),
  };
}

function parseComparison(value: unknown): DashboardOverview["comparison"] {
  if (!isRecord(value) || !isNullableNumber(value.percentageChange)) {
    throw invalidResponse();
  }
  return {
    previousFrom: stringField(value, "previousFrom"),
    previousTo: stringField(value, "previousTo"),
    previousFeedbackCount: numberField(value, "previousFeedbackCount"),
    currentFeedbackCount: numberField(value, "currentFeedbackCount"),
    percentageChange: value.percentageChange,
  };
}

function parsePeriod(value: unknown): DashboardPeriod {
  if (!isRecord(value)) throw invalidResponse();
  return { from: stringField(value, "from"), to: stringField(value, "to") };
}

function parseMetricMap(value: unknown): Record<string, DashboardMetric> {
  if (!isRecord(value)) throw invalidResponse();
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => {
      if (!isRecord(item)) throw invalidResponse();
      return [
        key,
        {
          count: numberField(item, "count"),
          percentage: numberField(item, "percentage"),
        },
      ];
    }),
  );
}

function parseNumberMap(value: unknown): Record<string, number> {
  if (value === null || value === undefined) return {};
  if (!isRecord(value)) throw invalidResponse();
  return Object.fromEntries(
    Object.entries(value).map(([key, count]) => {
      if (typeof count !== "number" || !Number.isFinite(count)) throw invalidResponse();
      return [key, count];
    }),
  );
}

function parseTextCollection(value: unknown): string[] {
  if (value === null || value === undefined) return [];
  if (!Array.isArray(value)) throw invalidResponse();
  return value.flatMap((item) => {
    if (typeof item === "string") return [item];
    if (isRecord(item)) {
      const text = Object.values(item).find((candidate) => typeof candidate === "string");
      return typeof text === "string" ? [text] : [];
    }
    return [];
  });
}

function parseArray<T>(value: unknown, parseItem: (item: unknown) => T): T[] {
  if (!Array.isArray(value)) throw invalidResponse();
  return value.map(parseItem);
}

function stringField(value: Record<string, unknown>, key: string): string {
  if (typeof value[key] !== "string") throw invalidResponse();
  return value[key];
}

function numberField(value: Record<string, unknown>, key: string): number {
  if (typeof value[key] !== "number" || !Number.isFinite(value[key])) {
    throw invalidResponse();
  }
  return value[key];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

function isNullableNumber(value: unknown): value is number | null {
  return value === null || (typeof value === "number" && Number.isFinite(value));
}

function invalidResponse(): ApiError {
  return new ApiError(
    "INVALID_RESPONSE",
    "The server returned invalid dashboard data.",
  );
}
