import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type { PageResponse } from "../../../types/api";
import type {
  CreateFeedbackRequestInput,
  FeedbackQuestion,
  FeedbackQuestionInput,
  FeedbackQuestionPage,
  FeedbackRequest,
  FeedbackRequestPage,
  FeedbackRequestStatus,
} from "../types/feedback.types";

const QUESTION_PATH = "/api/feedback-questions";
const REQUEST_PATH = "/api/feedback-requests";

export async function listQuestions(options: {
  active?: boolean;
  page?: number;
  signal?: AbortSignal;
} = {}): Promise<FeedbackQuestionPage> {
  const query = new URLSearchParams({
    page: String(options.page ?? 0),
    size: "50",
  });
  if (options.active !== undefined) {
    query.set("active", String(options.active));
  }
  const response = await apiRequest<unknown>(`${QUESTION_PATH}?${query}`, {
    auth: "required",
    signal: options.signal,
  });
  return parsePage(response, parseFeedbackQuestion);
}

export async function saveQuestion(
  input: FeedbackQuestionInput,
  questionId?: string,
): Promise<FeedbackQuestion> {
  const response = await apiRequest<unknown>(
    questionId
      ? `${QUESTION_PATH}/${encodeURIComponent(questionId)}`
      : QUESTION_PATH,
    {
      method: questionId ? "PUT" : "POST",
      body: input,
      auth: "required",
    },
  );
  return parseFeedbackQuestion(response);
}

export async function setQuestionActive(
  questionId: string,
  active: boolean,
): Promise<FeedbackQuestion> {
  const response = await apiRequest<unknown>(
    `${QUESTION_PATH}/${encodeURIComponent(questionId)}/status`,
    { method: "PATCH", body: { active }, auth: "required" },
  );
  return parseFeedbackQuestion(response);
}

export async function listFeedbackRequests(options: {
  status?: FeedbackRequestStatus;
  page?: number;
  signal?: AbortSignal;
} = {}): Promise<FeedbackRequestPage> {
  const query = new URLSearchParams({
    page: String(options.page ?? 0),
    size: "20",
  });
  if (options.status) {
    query.set("status", options.status);
  }
  const response = await apiRequest<unknown>(`${REQUEST_PATH}?${query}`, {
    auth: "required",
    signal: options.signal,
  });
  return parsePage(response, parseFeedbackRequest);
}

export async function getFeedbackRequest(
  requestId: string,
  signal?: AbortSignal,
): Promise<FeedbackRequest> {
  const response = await apiRequest<unknown>(
    `${REQUEST_PATH}/${encodeURIComponent(requestId)}`,
    { auth: "required", signal },
  );
  return parseFeedbackRequest(response);
}

export async function createFeedbackRequest(
  input: CreateFeedbackRequestInput,
): Promise<FeedbackRequest> {
  const response = await apiRequest<unknown>(REQUEST_PATH, {
    method: "POST",
    body: input,
    auth: "required",
  });
  return parseFeedbackRequest(response);
}

export async function cancelFeedbackRequest(
  requestId: string,
): Promise<FeedbackRequest> {
  const response = await apiRequest<unknown>(
    `${REQUEST_PATH}/${encodeURIComponent(requestId)}/cancel`,
    { method: "PATCH", auth: "required" },
  );
  return parseFeedbackRequest(response);
}

export async function retryFeedbackRequest(
  requestId: string,
): Promise<FeedbackRequest> {
  const response = await apiRequest<unknown>(
    `${REQUEST_PATH}/${encodeURIComponent(requestId)}/retry`,
    { method: "POST", auth: "required" },
  );
  return parseFeedbackRequest(response);
}

function parsePage<T>(
  value: unknown,
  parseItem: (item: unknown) => T,
): PageResponse<T> {
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
    content: value.content.map(parseItem),
    pageNumber: value.pageNumber,
    pageSize: value.pageSize,
    totalElements: value.totalElements,
    totalPages: value.totalPages,
    hasNext: value.hasNext,
    hasPrevious: value.hasPrevious,
  };
}

function parseFeedbackQuestion(value: unknown): FeedbackQuestion {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.questionText !== "string" ||
    !isQuestionType(value.questionType) ||
    typeof value.required !== "boolean" ||
    typeof value.active !== "boolean" ||
    !isNumber(value.displayOrder) ||
    typeof value.createdAt !== "string" ||
    typeof value.updatedAt !== "string"
  ) {
    throw invalidResponse();
  }
  return {
    id: value.id,
    questionText: value.questionText,
    questionType: value.questionType,
    required: value.required,
    active: value.active,
    displayOrder: value.displayOrder,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  };
}

function parseFeedbackRequest(value: unknown): FeedbackRequest {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    !isNullableString(value.feedbackId) ||
    typeof value.transactionId !== "string" ||
    typeof value.customerId !== "string" ||
    !isRequestStatus(value.status) ||
    typeof value.scheduledAt !== "string" ||
    !isNullableString(value.sentAt) ||
    !isNullableString(value.deliveredAt) ||
    !isNullableString(value.completedAt) ||
    typeof value.expiresAt !== "string" ||
    typeof value.createdAt !== "string" ||
    !isNullableString(value.feedbackLink)
  ) {
    throw invalidResponse();
  }
  return {
    id: value.id,
    feedbackId: value.feedbackId,
    transactionId: value.transactionId,
    customerId: value.customerId,
    status: value.status,
    scheduledAt: value.scheduledAt,
    sentAt: value.sentAt,
    deliveredAt: value.deliveredAt,
    completedAt: value.completedAt,
    expiresAt: value.expiresAt,
    createdAt: value.createdAt,
    feedbackLink: value.feedbackLink,
  };
}

function isQuestionType(value: unknown): value is FeedbackQuestion["questionType"] {
  return value === "TEXT" || value === "RATING" || value === "YES_NO";
}

function isRequestStatus(value: unknown): value is FeedbackRequestStatus {
  return (
    value === "PENDING" ||
    value === "SCHEDULED" ||
    value === "SENT" ||
    value === "DELIVERED" ||
    value === "FAILED" ||
    value === "COMPLETED" ||
    value === "EXPIRED" ||
    value === "CANCELLED"
  );
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function invalidResponse(): ApiError {
  return new ApiError(
    "INVALID_RESPONSE",
    "The server returned invalid feedback data.",
  );
}
