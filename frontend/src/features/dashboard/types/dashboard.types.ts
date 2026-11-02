export interface DashboardPeriod {
  from: string;
  to: string;
}

export interface DashboardMetric {
  count: number;
  percentage: number;
}

export interface AnalysisCoverage {
  totalFeedback: number;
  analyzed: number;
  pending: number;
  failed: number;
  analyzedPercentage: number;
}

export interface DashboardOverview {
  period: DashboardPeriod;
  feedbackMetrics: {
    totalCompletedFeedback: number;
    averageRating: number;
    positiveFeedback: number;
    neutralFeedback: number;
    negativeFeedback: number;
    mixedFeedback: number;
  };
  ratingDistribution: Record<string, DashboardMetric>;
  sentimentDistribution: Record<string, DashboardMetric>;
  analysisCoverage: AnalysisCoverage;
  comparison: {
    previousFrom: string;
    previousTo: string;
    previousFeedbackCount: number;
    currentFeedbackCount: number;
    percentageChange: number | null;
  } | null;
}

export interface DashboardTrendPoint {
  periodStart: string;
  periodEnd: string;
  feedbackCount: number;
  averageRating: number;
  sentimentDistribution: Record<string, number>;
  analysisCoverage: AnalysisCoverage;
}

export interface DashboardTrends {
  from: string;
  to: string;
  interval: "DAILY" | "WEEKLY" | "MONTHLY";
  points: DashboardTrendPoint[];
}

export interface DashboardTopic {
  topic: string;
  feedbackCount: number;
  positiveMentions: number;
  neutralMentions: number;
  negativeMentions: number;
  mixedMentions: number;
  analyzedFeedbackPercentage: number;
  sentimentDistribution: Record<string, number>;
  from: string;
  to: string;
}

export interface DashboardAspect {
  aspect: string;
  mentions: number;
  sentimentDistribution: Record<string, number>;
  averageSentimentConfidence: number;
  from: string;
  to: string;
}

export interface DashboardIssue {
  issue: string;
  description: string;
  distinctFeedbackReports: number;
  severityDistribution: Record<string, number>;
  relatedTopics: string;
  firstReportedAt: string;
  mostRecentlyReportedAt: string;
  sentimentDistribution: Record<string, number>;
}

export interface DashboardHighlight {
  topicOrAspect: string;
  explanation: string;
  supportingFeedbackRecords: number;
  sentiment: string;
}

export interface DashboardHighlights {
  from: string;
  to: string;
  positive: DashboardHighlight[];
  negative: DashboardHighlight[];
}

export interface DashboardSummary {
  id: string;
  from: string;
  to: string;
  summary: string;
  positiveObservations: string[];
  mainConcerns: string[];
  recurringIssues: string[];
  improvementRecommendations: string[];
  feedbackCountUsed: number;
  latestFeedbackAt: string | null;
  generatedAt: string;
  analysisCoverage: AnalysisCoverage;
}

export interface DashboardActivity {
  from: string;
  to: string;
  totalRequests: number;
  pendingRequests: number;
  scheduledRequests: number;
  sentRequests: number;
  deliveredRequests: number;
  failedRequests: number;
  completedRequests: number;
  expiredRequests: number;
  conversationsInProgress: number;
  conversationsCompleted: number;
  analysisPending: number;
  analysisFailed: number;
}

export interface DashboardData {
  overview: DashboardOverview;
  trends: DashboardTrends;
  topics: DashboardTopic[];
  aspects: DashboardAspect[];
  issues: DashboardIssue[];
  highlights: DashboardHighlights;
  summary: DashboardSummary | null;
  activity: DashboardActivity;
}

export interface DashboardDateRange {
  from?: string;
  to?: string;
}
