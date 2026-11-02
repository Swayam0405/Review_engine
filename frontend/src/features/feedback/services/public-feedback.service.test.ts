import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "../../../lib/api/api-client";
import {
  getConversation,
  sendConversationMessage,
  startOrResumeConversation,
  submitFeedback,
  validateFeedbackLink,
} from "./public-feedback.service";

vi.mock("../../../lib/api/api-client", () => ({
  apiRequest: vi.fn(),
}));

const question = {
  id: "question-1",
  questionText: "How was it?",
  questionType: "TEXT",
  required: true,
  active: true,
  displayOrder: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};
const conversation = {
  status: "IN_PROGRESS",
  startedAt: "2026-01-01T00:00:00Z",
  completedAt: null,
  messages: [],
  questions: [question],
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(apiRequest).mockImplementation(async (path) => {
    if (path.endsWith("/submit")) {
      return {
        received: true,
        message: "Received",
        feedbackCompleted: true,
        conversationCompleted: true,
        completionMessage: "Thank you.",
        googleReviewAvailable: false,
        googleReviewUrl: null,
        reviewInvitationMessage: null,
      };
    }
    if (
      path.endsWith("/conversation") ||
      path.endsWith("/conversation/messages")
    ) {
      return conversation;
    }
    if (!path.endsWith("/conversation")) {
      return {
        businessName: "Example Business",
        expiresAt: "2026-02-01T00:00:00Z",
        questions: [question],
        completion: null,
      };
    }
    return conversation;
  });
});

describe("public feedback service", () => {
  it("uses no-auth public link and conversation requests", async () => {
    const signal = new AbortController().signal;
    await validateFeedbackLink("opaque/token", signal);
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/public/feedback/opaque%2Ftoken",
      { auth: "none", signal },
    );

    await startOrResumeConversation("opaque-token");
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/public/feedback/opaque-token/conversation",
      { method: "POST", auth: "none" },
    );

    await getConversation("opaque-token", signal);
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/public/feedback/opaque-token/conversation",
      { auth: "none", signal },
    );

    await sendConversationMessage("opaque-token", { content: "Good", rating: 5 });
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/public/feedback/opaque-token/conversation/messages",
      { method: "POST", body: { content: "Good", rating: 5 }, auth: "none" },
    );
  });

  it("posts typed feedback to the token endpoint without owner credentials", async () => {
    const input = {
      rating: 5,
      comment: "Helpful team",
      answers: [{ questionId: "question-1", answerText: "Excellent" }],
    };
    const result = await submitFeedback("opaque-token", input);
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/public/feedback/opaque-token/submit",
      { method: "POST", body: input, auth: "none" },
    );
    expect(result.feedbackCompleted).toBe(true);
    expect(apiRequest).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ auth: "required" }),
    );
  });

  it("parses a backend completion without exposing questions or starting a conversation", async () => {
    const completion = {
      received: true,
      message: "Thank you.",
      feedbackCompleted: true,
      conversationCompleted: true,
      completionMessage: "Thank you. Your feedback has been received.",
      googleReviewAvailable: true,
      googleReviewUrl: "https://g.page/r/example/review",
      reviewInvitationMessage: "Optional public review.",
    };
    vi.mocked(apiRequest).mockResolvedValue({
      businessName: "Example Business",
      expiresAt: "2026-02-01T00:00:00Z",
      questions: [],
      completion,
    });

    const page = await validateFeedbackLink("opaque-token");
    expect(page.completion).toEqual(completion);
    expect(page.questions).toEqual([]);
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/public/feedback/opaque-token",
      { auth: "none", signal: undefined },
    );
  });
});
