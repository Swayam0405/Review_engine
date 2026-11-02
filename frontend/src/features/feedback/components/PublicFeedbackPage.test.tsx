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
import { ApiError } from "../../../lib/api/api-error";
import { PublicFeedbackPage } from "./PublicFeedbackPage";

const mocks = vi.hoisted(() => ({
  validateFeedbackLink: vi.fn(),
  startOrResumeConversation: vi.fn(),
  sendConversationMessage: vi.fn(),
  submitFeedback: vi.fn(),
}));

vi.mock("../services/public-feedback.service", () => ({
  validateFeedbackLink: mocks.validateFeedbackLink,
  startOrResumeConversation: mocks.startOrResumeConversation,
  sendConversationMessage: mocks.sendConversationMessage,
  submitFeedback: mocks.submitFeedback,
}));

const conversation = {
  status: "IN_PROGRESS",
  startedAt: "2026-01-01T00:00:00Z",
  completedAt: null,
  messages: [],
  questions: [],
};
const page = {
  businessName: "Example Business",
  expiresAt: "2026-02-01T00:00:00Z",
  questions: [],
  completion: null,
};
const completion = {
  received: true,
  message: "Thank you.",
  feedbackCompleted: true,
  conversationCompleted: true,
  completionMessage: "Thank you. Your feedback has been received.",
  googleReviewAvailable: true,
  googleReviewUrl: "https://g.page/r/example/review",
  reviewInvitationMessage: "Share an optional public review.",
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.validateFeedbackLink.mockResolvedValue(page);
  mocks.startOrResumeConversation.mockResolvedValue(conversation);
  mocks.sendConversationMessage.mockResolvedValue(conversation);
});

afterEach(cleanup);

describe("public feedback completion recovery", () => {
  it("restores completion and review facilitation without starting a conversation", async () => {
    mocks.validateFeedbackLink.mockResolvedValue({
      ...page,
      questions: [],
      completion,
    });

    render(<PublicFeedbackPage token="opaque-token" />);

    expect(await screen.findByText("Thank you. Your feedback has been received.")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Open Google review page" }).getAttribute("href"))
      .toBe(completion.googleReviewUrl);
    expect(mocks.startOrResumeConversation).not.toHaveBeenCalled();
  });

  it("checks authoritative status after an ambiguous submit without replaying the POST", async () => {
    mocks.validateFeedbackLink
      .mockResolvedValueOnce(page)
      .mockResolvedValueOnce({ ...page, completion });
    mocks.submitFeedback.mockRejectedValue(
      new ApiError("NETWORK_ERROR", "Unable to reach the server."),
    );

    render(<PublicFeedbackPage token="opaque-token" />);
    await screen.findByText("Start by telling us about your experience.");
    fireEvent.click(
      within(screen.getByRole("group", { name: "Overall rating *" }))
        .getByRole("button", { name: "5" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));

    expect(await screen.findByText("Thank you. Your feedback has been received.")).toBeTruthy();
    expect(mocks.submitFeedback).toHaveBeenCalledTimes(1);
    expect(mocks.validateFeedbackLink).toHaveBeenCalledTimes(2);
  });

  it("allows explicit resubmission only after the backend confirms no completion", async () => {
    mocks.validateFeedbackLink
      .mockResolvedValueOnce(page)
      .mockResolvedValueOnce(page);
    mocks.submitFeedback
      .mockRejectedValueOnce(new ApiError("NETWORK_ERROR", "Unable to reach the server."))
      .mockResolvedValueOnce(completion);

    render(<PublicFeedbackPage token="opaque-token" />);
    await screen.findByText("Start by telling us about your experience.");
    fireEvent.click(
      within(screen.getByRole("group", { name: "Overall rating *" }))
        .getByRole("button", { name: "5" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));
    expect(await screen.findByText(/did not mark your feedback as complete/)).toBeTruthy();
    expect(mocks.submitFeedback).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));
    await waitFor(() => expect(mocks.submitFeedback).toHaveBeenCalledTimes(2));
  });
});
