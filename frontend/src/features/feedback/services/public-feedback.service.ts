import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type {
  ConversationMessage,
  FeedbackConfirmation,
  FeedbackConversation,
  FeedbackQuestionType,
  PublicFeedbackPage,
  FeedbackSubmissionInput,
} from "../types/feedback.types";

function path(token: string, suffix = ""): string {
  return `/api/public/feedback/${encodeURIComponent(token)}${suffix}`;
}

export async function validateFeedbackLink(
  token: string,
  signal?: AbortSignal,
): Promise<PublicFeedbackPage> {
  return parsePublicPage(
    await apiRequest<unknown>(path(token), {
      auth: "none",
      signal,
    }),
  );
}

export async function startOrResumeConversation(
  token: string,
): Promise<FeedbackConversation> {
  return parseConversation(
    await apiRequest<unknown>(path(token, "/conversation"), {
      method: "POST",
      auth: "none",
    }),
  );
}

export async function getConversation(
  token: string,
  signal?: AbortSignal,
): Promise<FeedbackConversation> {
  return parseConversation(
    await apiRequest<unknown>(path(token, "/conversation"), {
      auth: "none",
      signal,
    }),
  );
}

export async function sendConversationMessage(
  token: string,
  input: { content: string; rating?: number },
): Promise<FeedbackConversation> {
  return parseConversation(
    await apiRequest<unknown>(path(token, "/conversation/messages"), {
      method: "POST",
      body: input,
      auth: "none",
    }),
  );
}

export async function submitFeedback(
  token: string,
  input: FeedbackSubmissionInput,
): Promise<FeedbackConfirmation> {
  return parseConfirmation(
    await apiRequest<unknown>(path(token, "/submit"), {
      method: "POST",
      body: input,
      auth: "none",
    }),
  );
}

function parsePublicPage(value: unknown): PublicFeedbackPage {
  if (
    !isRecord(value) ||
    typeof value.businessName !== "string" ||
    typeof value.expiresAt !== "string" ||
    !Array.isArray(value.questions)
  ) {
    throw invalidResponse();
  }
  return {
    businessName: value.businessName,
    expiresAt: value.expiresAt,
    questions: value.questions.map(parseQuestion),
    completion:
      value.completion === null || value.completion === undefined
        ? null
        : parseConfirmation(value.completion),
  };
}

function parseQuestion(value: unknown) {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.questionText !== "string" ||
    !isQuestionType(value.questionType) ||
    typeof value.required !== "boolean" ||
    typeof value.active !== "boolean" ||
    typeof value.displayOrder !== "number" ||
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

function parseConversation(value: unknown): FeedbackConversation {
  if (
    !isRecord(value) ||
    !isConversationStatus(value.status) ||
    typeof value.startedAt !== "string" ||
    !isNullableString(value.completedAt) ||
    !Array.isArray(value.messages) ||
    !Array.isArray(value.questions)
  ) {
    throw invalidResponse();
  }
  return {
    status: value.status,
    startedAt: value.startedAt,
    completedAt: value.completedAt,
    messages: value.messages.map(parseMessage),
    questions: value.questions.map(parseQuestion),
  };
}

function parseMessage(value: unknown): ConversationMessage {
  if (
    !isRecord(value) ||
    !isSender(value.senderType) ||
    !isMessageType(value.messageType) ||
    typeof value.content !== "string" ||
    typeof value.createdAt !== "string"
  ) {
    throw invalidResponse();
  }
  return {
    senderType: value.senderType,
    messageType: value.messageType,
    content: value.content,
    createdAt: value.createdAt,
  };
}

function parseConfirmation(value: unknown): FeedbackConfirmation {
  if (
    !isRecord(value) ||
    typeof value.received !== "boolean" ||
    typeof value.message !== "string" ||
    typeof value.feedbackCompleted !== "boolean" ||
    typeof value.conversationCompleted !== "boolean" ||
    typeof value.completionMessage !== "string" ||
    typeof value.googleReviewAvailable !== "boolean" ||
    !isNullableString(value.googleReviewUrl) ||
    !isNullableString(value.reviewInvitationMessage)
  ) {
    throw invalidResponse();
  }
  return {
    received: value.received,
    message: value.message,
    feedbackCompleted: value.feedbackCompleted,
    conversationCompleted: value.conversationCompleted,
    completionMessage: value.completionMessage,
    googleReviewAvailable: value.googleReviewAvailable,
    googleReviewUrl: value.googleReviewUrl,
    reviewInvitationMessage: value.reviewInvitationMessage,
  };
}

function isQuestionType(value: unknown): value is FeedbackQuestionType {
  return value === "TEXT" || value === "RATING" || value === "YES_NO";
}

function isConversationStatus(
  value: unknown,
): value is FeedbackConversation["status"] {
  return (
    value === "NOT_STARTED" ||
    value === "IN_PROGRESS" ||
    value === "COMPLETED" ||
    value === "ABANDONED" ||
    value === "EXPIRED"
  );
}

function isSender(value: unknown): value is ConversationMessage["senderType"] {
  return value === "CUSTOMER" || value === "SYSTEM" || value === "AI";
}

function isMessageType(
  value: unknown,
): value is ConversationMessage["messageType"] {
  return (
    value === "TEXT" ||
    value === "QUESTION" ||
    value === "ANSWER" ||
    value === "RATING" ||
    value === "SYSTEM_EVENT"
  );
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function invalidResponse(): ApiError {
  return new ApiError(
    "INVALID_RESPONSE",
    "The server returned invalid feedback data.",
  );
}
