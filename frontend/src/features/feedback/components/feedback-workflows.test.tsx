// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import type { ReactNode } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { FeedbackQuestionsPage } from "./FeedbackQuestionsPage";
import { FeedbackRequestDetailsPage } from "./FeedbackRequestDetailsPage";
import { FeedbackRequestFormPage } from "./FeedbackRequestFormPage";
import { FeedbackRequestsPage } from "./FeedbackRequestsPage";
import { PublicFeedbackPage } from "./PublicFeedbackPage";

const mocks = vi.hoisted(() => ({
  listQuestions: vi.fn(),
  saveQuestion: vi.fn(),
  setQuestionActive: vi.fn(),
  listFeedbackRequests: vi.fn(),
  getFeedbackRequest: vi.fn(),
  createFeedbackRequest: vi.fn(),
  cancelFeedbackRequest: vi.fn(),
  retryFeedbackRequest: vi.fn(),
  listTransactions: vi.fn(),
  validateFeedbackLink: vi.fn(),
  startOrResumeConversation: vi.fn(),
  sendConversationMessage: vi.fn(),
  submitFeedback: vi.fn(),
}));

vi.mock("next/link", async () => {
  const React = await import("react");
  return {
    default: ({
      href,
      children,
      ...props
    }: {
      href: string;
      children: ReactNode;
      [key: string]: unknown;
    }) => React.createElement("a", { ...props, href }, children),
  };
});

vi.mock("../../business/components/BusinessRouteGate", () => ({
  BusinessRouteGate: ({ children }: { children: ReactNode }) => children,
}));

vi.mock("../../transactions/services/transaction.service", () => ({
  listTransactions: mocks.listTransactions,
}));

vi.mock("../services/feedback-owner.service", () => ({
  listQuestions: mocks.listQuestions,
  saveQuestion: mocks.saveQuestion,
  setQuestionActive: mocks.setQuestionActive,
  listFeedbackRequests: mocks.listFeedbackRequests,
  getFeedbackRequest: mocks.getFeedbackRequest,
  createFeedbackRequest: mocks.createFeedbackRequest,
  cancelFeedbackRequest: mocks.cancelFeedbackRequest,
  retryFeedbackRequest: mocks.retryFeedbackRequest,
}));

vi.mock("../services/public-feedback.service", () => ({
  validateFeedbackLink: mocks.validateFeedbackLink,
  startOrResumeConversation: mocks.startOrResumeConversation,
  sendConversationMessage: mocks.sendConversationMessage,
  submitFeedback: mocks.submitFeedback,
}));

const question = {
  id: "question-1",
  questionText: "Describe your experience",
  questionType: "TEXT" as const,
  required: true,
  active: true,
  displayOrder: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};
const page = {
  content: [question],
  pageNumber: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};
const transactionPage = {
  content: [
    {
      id: "transaction-1",
      customerId: "customer-1",
      customerName: "Alex Customer",
      referenceCode: "ORDER-12",
      status: "COMPLETED" as const,
      completedAt: "2026-10-31T12:00:00Z",
      createdAt: "2026-10-31T12:00:00Z",
      updatedAt: "2026-10-31T12:00:00Z",
    },
  ],
  pageNumber: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};
const request = {
  id: "request-1",
  feedbackId: null,
  transactionId: "transaction-1",
  customerId: "customer-1",
  status: "PENDING" as const,
  scheduledAt: "2026-10-31T12:00:00Z",
  sentAt: null,
  deliveredAt: null,
  completedAt: null,
  expiresAt: "2026-11-30T12:00:00Z",
  createdAt: "2026-10-31T12:00:00Z",
  feedbackLink: null,
};
const publicPage = {
  businessName: "Review Shop",
  expiresAt: "2026-11-30T12:00:00Z",
  questions: [question],
  completion: null,
};
const conversation = {
  status: "IN_PROGRESS" as const,
  startedAt: "2026-10-31T12:00:00Z",
  completedAt: null,
  messages: [],
  questions: [question],
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.listQuestions.mockResolvedValue(page);
  mocks.saveQuestion.mockResolvedValue(question);
  mocks.setQuestionActive.mockResolvedValue(question);
  mocks.listFeedbackRequests.mockResolvedValue({ ...page, content: [request] });
  mocks.getFeedbackRequest.mockResolvedValue(request);
  mocks.cancelFeedbackRequest.mockResolvedValue({ ...request, status: "CANCELLED" });
  mocks.retryFeedbackRequest.mockResolvedValue({ ...request, status: "PENDING" });
  mocks.listTransactions.mockResolvedValue(transactionPage);
  mocks.createFeedbackRequest.mockResolvedValue({
    ...request,
    feedbackLink: "https://reviews.example/feedback/opaque-token",
  });
  mocks.validateFeedbackLink.mockResolvedValue(publicPage);
  mocks.startOrResumeConversation.mockResolvedValue(conversation);
  mocks.sendConversationMessage.mockResolvedValue(conversation);
  mocks.submitFeedback.mockResolvedValue({
    received: true,
    message: "Feedback received",
    feedbackCompleted: true,
    conversationCompleted: true,
    completionMessage: "Thank you for sharing your experience.",
    googleReviewAvailable: true,
    googleReviewUrl: "https://reviews.example/google",
    reviewInvitationMessage: "Would you also leave a Google review?",
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("owner feedback workflows", () => {
  it("creates a question after validating required text", async () => {
    mocks.listQuestions.mockResolvedValue({ ...page, content: [] });
    render(<FeedbackQuestionsPage />);
    await screen.findByText("No feedback questions match this view.");
    fireEvent.click(screen.getByRole("button", { name: "Add question" }));
    fireEvent.click(screen.getByRole("button", { name: "Create question" }));
    expect(await screen.findByText("Question text is required.")).toBeTruthy();
    expect(mocks.saveQuestion).not.toHaveBeenCalled();

    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: "  How was your visit?  " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create question" }));
    await waitFor(() =>
      expect(mocks.saveQuestion).toHaveBeenCalledWith(
        expect.objectContaining({ questionText: "How was your visit?" }),
        undefined,
      ),
    );
  });

  it("creates only once and displays the exact returned public link", async () => {
    let resolveRequest!: (
      value: Omit<typeof request, "feedbackLink"> & { feedbackLink: string },
    ) => void;
    mocks.createFeedbackRequest.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = resolve;
        }),
    );
    render(<FeedbackRequestFormPage />);
    fireEvent.change(await screen.findByLabelText("Completed transaction *"), {
      target: { value: "transaction-1" },
    });
    const createButton = screen.getByRole("button", {
      name: "Create feedback request",
    });
    fireEvent.click(createButton);
    fireEvent.click(createButton);
    expect(mocks.createFeedbackRequest).toHaveBeenCalledTimes(1);
    expect(mocks.createFeedbackRequest).toHaveBeenCalledWith({
      transactionId: "transaction-1",
    });
    resolveRequest({
      ...request,
      feedbackLink: "https://reviews.example/feedback/opaque-token",
    });
    const link = await screen.findByRole("link", {
      name: "https://reviews.example/feedback/opaque-token",
    });
    expect(link.getAttribute("href")).toBe(
      "https://reviews.example/feedback/opaque-token",
    );
  });

  it("cancels a request and refreshes the backend status", async () => {
    mocks.getFeedbackRequest
      .mockResolvedValueOnce(request)
      .mockResolvedValueOnce({ ...request, status: "CANCELLED" });
    render(<FeedbackRequestDetailsPage requestId="request-1" />);
    await screen.findByText("PENDING");
    fireEvent.click(screen.getByRole("button", { name: "Cancel request" }));
    await screen.findByText("CANCELLED");
    expect(mocks.cancelFeedbackRequest).toHaveBeenCalledWith("request-1");
    expect(mocks.getFeedbackRequest).toHaveBeenCalledTimes(2);
  });

  it("lists backend request statuses without exposing feedback links", async () => {
    render(<FeedbackRequestsPage />);
    await screen.findByText("PENDING");
    expect(screen.getByRole("link", { name: "View details" })).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Open customer feedback page" })).toBeNull();
  });
});

describe("public customer feedback workflow", () => {
  it("restores the conversation, submits typed answers, and waits for backend confirmation", async () => {
    render(<PublicFeedbackPage token="opaque-token" />);
    expect(await screen.findByRole("heading", { name: "Review Shop" })).toBeTruthy();
    expect(mocks.startOrResumeConversation).toHaveBeenCalledWith("opaque-token");

    fireEvent.change(screen.getByLabelText("Describe your experience *"), {
      target: { value: "Excellent service" },
    });
    const ratingGroup = screen.getByRole("group", { name: "Overall rating *" });
    fireEvent.click(within(ratingGroup).getByRole("button", { name: "5" }));
    fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));

    await waitFor(() =>
      expect(mocks.submitFeedback).toHaveBeenCalledWith("opaque-token", {
        rating: 5,
        answers: [{ questionId: "question-1", answerText: "Excellent service" }],
      }),
    );
    expect(
      await screen.findByRole("heading", { name: "Thank you for your feedback" }),
    ).toBeTruthy();
    expect(
      screen.getByRole("link", { name: "Open Google review page" }).getAttribute("href"),
    ).toBe("https://reviews.example/google");
  });

  it.each([
    [1, "negative"],
    [3, "neutral"],
    [5, "positive"],
  ])(
    "offers the backend-provided review invitation after confirmed %s-star (%s) feedback",
    async (rating) => {
      const currentPath = window.location.pathname;
      render(<PublicFeedbackPage token="opaque-token" />);
      await screen.findByRole("heading", { name: "Review Shop" });
      fireEvent.change(screen.getByLabelText("Describe your experience *"), {
        target: { value: "My experience" },
      });
      const ratingGroup = screen.getByRole("group", { name: "Overall rating *" });
      fireEvent.click(within(ratingGroup).getByRole("button", { name: String(rating) }));
      fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));

      const invitation = await screen.findByRole("link", {
        name: "Open Google review page",
      });
      expect(invitation.getAttribute("href")).toBe("https://reviews.example/google");
      expect(invitation.getAttribute("target")).toBe("_blank");
      expect(window.location.pathname).toBe(currentPath);
      expect(mocks.submitFeedback).toHaveBeenCalledWith("opaque-token", {
        rating,
        answers: [{ questionId: "question-1", answerText: "My experience" }],
      });
    },
  );

  it("does not show a review link when the backend says it is unavailable", async () => {
    mocks.submitFeedback.mockResolvedValue({
      received: true,
      message: "Feedback received",
      feedbackCompleted: true,
      conversationCompleted: true,
      completionMessage: "Thank you for sharing your experience.",
      googleReviewAvailable: false,
      googleReviewUrl: null,
      reviewInvitationMessage: null,
    });
    render(<PublicFeedbackPage token="opaque-token" />);
    await screen.findByRole("heading", { name: "Review Shop" });
    fireEvent.change(screen.getByLabelText("Describe your experience *"), {
      target: { value: "My experience" },
    });
    const ratingGroup = screen.getByRole("group", { name: "Overall rating *" });
    fireEvent.click(within(ratingGroup).getByRole("button", { name: "3" }));
    fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));
    expect(await screen.findByRole("heading", {
      name: "Thank you for your feedback",
    })).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Open Google review page" })).toBeNull();
  });

  it("does not automatically resubmit after an ambiguous network failure", async () => {
    mocks.submitFeedback.mockRejectedValue(
      new ApiError("NETWORK_ERROR", "Offline."),
    );
    render(<PublicFeedbackPage token="opaque-token" />);
    await screen.findByRole("heading", { name: "Review Shop" });
    fireEvent.change(screen.getByLabelText("Describe your experience *"), {
      target: { value: "Good" },
    });
    const ratingGroup = screen.getByRole("group", { name: "Overall rating *" });
    fireEvent.click(within(ratingGroup).getByRole("button", { name: "4" }));
    fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));
    expect(await screen.findByText(/did not mark your feedback as complete/i)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Submit feedback" })).toBeTruthy();
    expect(mocks.submitFeedback).toHaveBeenCalledTimes(1);
  });

  it("does not allow repeat submission when the backend reports an unavailable token", async () => {
    mocks.validateFeedbackLink
      .mockResolvedValueOnce(publicPage)
      .mockRejectedValueOnce(new ApiError("NOT_FOUND", "Unavailable.", 404));
    mocks.submitFeedback.mockRejectedValue(
      new ApiError("NOT_FOUND", "Unavailable.", 404),
    );
    render(<PublicFeedbackPage token="opaque-token" />);
    await screen.findByRole("heading", { name: "Review Shop" });
    fireEvent.change(screen.getByLabelText("Describe your experience *"), {
      target: { value: "Good" },
    });
    const ratingGroup = screen.getByRole("group", { name: "Overall rating *" });
    fireEvent.click(within(ratingGroup).getByRole("button", { name: "4" }));
    fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));
    expect(await screen.findByText(/could not check the submission status/i)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Check submission status" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Submission status unknown" })).toBeTruthy();
    expect(mocks.submitFeedback).toHaveBeenCalledTimes(1);
  });

  it("distinguishes expired links from unavailable links", async () => {
    mocks.validateFeedbackLink.mockRejectedValue(
      new ApiError("HTTP_ERROR", "Expired.", 410),
    );
    render(<PublicFeedbackPage token="expired-token" />);
    expect(
      await screen.findByRole("heading", { name: "Feedback link expired" }),
    ).toBeTruthy();
    expect(mocks.startOrResumeConversation).not.toHaveBeenCalled();
  });
});
