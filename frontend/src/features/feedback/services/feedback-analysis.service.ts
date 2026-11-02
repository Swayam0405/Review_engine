import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type {
  FeedbackAnalysis,
  FeedbackAnalysisAspect,
  FeedbackAnalysisIssue,
  FeedbackAnalysisStatus,
  FeedbackAnalysisTopic,
} from "../types/feedback.types";

function analysisPath(feedbackId: string): string {
  return `/api/feedback/${encodeURIComponent(feedbackId)}/analysis`;
}

export async function getFeedbackAnalysis(
  feedbackId: string,
  signal?: AbortSignal,
): Promise<FeedbackAnalysisStatus> {
  const response = await apiRequest<unknown>(analysisPath(feedbackId), {
    auth: "required",
    signal,
  });
  return parseFeedbackAnalysisStatus(response);
}

export async function retryFeedbackAnalysis(feedbackId: string): Promise<void> {
  await apiRequest<null>(`${analysisPath(feedbackId)}/retry`, {
    method: "POST",
    auth: "required",
  });
}

function parseFeedbackAnalysisStatus(value: unknown): FeedbackAnalysisStatus {
  if (
    !isRecord(value) ||
    typeof value.feedbackId !== "string" ||
    typeof value.status !== "string" ||
    !isOptionalNullableString(value.safeError) ||
    !isOptionalNullableString(value.analyzedAt)
  ) {
    throw invalidResponse();
  }
  return {
    feedbackId: value.feedbackId,
    status: value.status,
    analysis:
      value.analysis === undefined || value.analysis === null
        ? null
        : parseAnalysis(value.analysis),
    safeError: nullableString(value.safeError),
    analyzedAt: nullableString(value.analyzedAt),
  };
}

function parseAnalysis(value: unknown): FeedbackAnalysis {
  if (!isRecord(value)) {
    throw invalidResponse();
  }
  const sentimentConfidence =
    typeof value.sentimentConfidence === "number" &&
    Number.isFinite(value.sentimentConfidence) &&
    value.sentimentConfidence >= 0 &&
    value.sentimentConfidence <= 1
      ? value.sentimentConfidence
      : null;

  return {
    sentiment: nullableString(value.sentiment),
    sentimentConfidence,
    sentimentSummary: nullableString(value.sentimentSummary),
    topics: parseCollection(value.topics, parseTopic),
    aspects: parseCollection(value.aspects, parseAspect),
    issues: parseCollection(value.issues, parseIssue),
    positiveHighlights: parseStrings(value.positiveHighlights),
    negativeHighlights: parseStrings(value.negativeHighlights),
    improvementOpportunities: parseStrings(value.improvementOpportunities),
  };
}

function parseTopic(value: unknown): FeedbackAnalysisTopic {
  if (!isRecord(value)) throw invalidResponse();
  return {
    name: nullableString(value.name),
    sentiment: nullableString(value.sentiment),
    evidence: nullableString(value.evidence),
  };
}

function parseAspect(value: unknown): FeedbackAnalysisAspect {
  if (!isRecord(value)) throw invalidResponse();
  return {
    name: nullableString(value.name),
    sentiment: nullableString(value.sentiment),
    explanation: nullableString(value.explanation),
    evidence: nullableString(value.evidence),
  };
}

function parseIssue(value: unknown): FeedbackAnalysisIssue {
  if (!isRecord(value)) throw invalidResponse();
  return {
    name: nullableString(value.name),
    description: nullableString(value.description),
    severity: nullableString(value.severity),
    topic: nullableString(value.topic),
    evidence: nullableString(value.evidence),
  };
}

function parseCollection<T>(
  value: unknown,
  parseItem: (item: unknown) => T,
): T[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) throw invalidResponse();
  return value.map(parseItem);
}

function parseStrings(value: unknown): string[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value) || !value.every((item) => typeof item === "string")) {
    throw invalidResponse();
  }
  return value;
}

function nullableString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function isOptionalNullableString(
  value: unknown,
): value is string | null | undefined {
  return value === undefined || value === null || typeof value === "string";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function invalidResponse(): ApiError {
  return new ApiError(
    "INVALID_RESPONSE",
    "The server returned invalid feedback analysis data.",
  );
}
