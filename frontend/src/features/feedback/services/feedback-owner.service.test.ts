import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "../../../lib/api/api-client";
import {
  cancelFeedbackRequest,
  createFeedbackRequest,
  listFeedbackRequests,
  listQuestions,
  retryFeedbackRequest,
  saveQuestion,
  setQuestionActive,
} from "./feedback-owner.service";

vi.mock("../../../lib/api/api-client", () => ({
  apiRequest: vi.fn(),
}));

const question = {
  id: "question-1",
  questionText: "How was your visit?",
  questionType: "RATING",
  required: true,
  active: true,
  displayOrder: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};
const request = {
  id: "request-1",
  feedbackId: null,
  transactionId: "transaction-1",
  customerId: "customer-1",
  status: "PENDING",
  scheduledAt: "2026-01-01T00:00:00Z",
  sentAt: null,
  deliveredAt: null,
  completedAt: null,
  expiresAt: "2026-02-01T00:00:00Z",
  createdAt: "2026-01-01T00:00:00Z",
  feedbackLink: "https://reviews.example/feedback/opaque-token",
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(apiRequest).mockImplementation(async (path) => {
    if (path.includes("feedback-questions")) {
      if (path.endsWith("status")) return question;
      if (path.includes("?")) {
        return {
          content: [question],
          pageNumber: 0,
          pageSize: 50,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
          hasPrevious: false,
        };
      }
      return question;
    }
    if (path.startsWith("/api/feedback-requests?")) {
      return {
        content: [request],
        pageNumber: 0,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      };
    }
    return request;
  });
});

describe("feedback owner service", () => {
  it("uses authenticated question list and CRUD contracts", async () => {
    const signal = new AbortController().signal;
    await listQuestions({ active: true, page: 2, signal });
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/feedback-questions?page=2&size=50&active=true",
      { auth: "required", signal },
    );

    await saveQuestion({
      questionText: "How was your visit?",
      questionType: "RATING",
      required: true,
      displayOrder: 0,
      active: true,
    });
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/feedback-questions",
      expect.objectContaining({ method: "POST", auth: "required" }),
    );

    await saveQuestion(
      {
        questionText: "How was your visit?",
        questionType: "RATING",
        required: true,
        displayOrder: 0,
        active: true,
      },
      "question-1",
    );
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/feedback-questions/question-1",
      expect.objectContaining({ method: "PUT", auth: "required" }),
    );

    await setQuestionActive("question-1", false);
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/feedback-questions/question-1/status",
      { method: "PATCH", body: { active: false }, auth: "required" },
    );
  });

  it("uses feedback request list, create, cancel, and retry contracts", async () => {
    await listFeedbackRequests({ status: "FAILED", page: 1 });
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/feedback-requests?page=1&size=20&status=FAILED",
      { auth: "required", signal: undefined },
    );
    expect((await listFeedbackRequests()).content[0]?.feedbackId).toBeNull();

    await createFeedbackRequest({ transactionId: "transaction-1" });
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/feedback-requests",
      expect.objectContaining({
        method: "POST",
        body: { transactionId: "transaction-1" },
        auth: "required",
      }),
    );
    expect(await createFeedbackRequest({ transactionId: "transaction-1" })).toHaveProperty(
      "feedbackLink",
      request.feedbackLink,
    );

    await cancelFeedbackRequest("request-1");
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/feedback-requests/request-1/cancel",
      { method: "PATCH", auth: "required" },
    );

    await retryFeedbackRequest("request-1");
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/feedback-requests/request-1/retry",
      { method: "POST", auth: "required" },
    );
  });
});
