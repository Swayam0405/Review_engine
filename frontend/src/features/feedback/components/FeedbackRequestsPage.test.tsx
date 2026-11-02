// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { FeedbackRequestsPage } from "./FeedbackRequestsPage";

const mocks = vi.hoisted(() => ({ listFeedbackRequests: vi.fn() }));

vi.mock("next/link", async () => {
  const React = await import("react");
  return {
    default: ({
      href,
      children,
      ...props
    }: { href: string; children: ReactNode; [key: string]: unknown }) =>
      React.createElement("a", { ...props, href }, children),
  };
});

vi.mock("../../business/components/BusinessRouteGate", () => ({
  BusinessRouteGate: ({ children }: { children: ReactNode }) => children,
}));

vi.mock("../services/feedback-owner.service", () => ({
  listFeedbackRequests: mocks.listFeedbackRequests,
}));

beforeEach(() => {
  vi.clearAllMocks();
  mocks.listFeedbackRequests.mockResolvedValue({
    content: [{
      id: "request-id",
      feedbackId: "actual-feedback-id",
      transactionId: "transaction-id",
      customerId: "customer-id",
      status: "COMPLETED",
      scheduledAt: "2026-01-01T00:00:00Z",
      sentAt: null,
      deliveredAt: null,
      completedAt: "2026-01-02T00:00:00Z",
      expiresAt: "2026-02-01T00:00:00Z",
      createdAt: "2026-01-01T00:00:00Z",
      feedbackLink: null,
    }],
    pageNumber: 0,
    pageSize: 20,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  });
});

afterEach(cleanup);

describe("feedback request analysis discovery", () => {
  it("links to analysis using the backend feedback UUID, not the request UUID", async () => {
    render(<FeedbackRequestsPage />);
    const analysisLink = await screen.findByRole("link", { name: "View analysis" });
    expect(analysisLink.getAttribute("href")).toBe(
      "/feedback/analysis/actual-feedback-id",
    );
    expect(analysisLink.getAttribute("href")).not.toContain("request-id");
  });

  it("shows a pending state when no feedback UUID is available", async () => {
    mocks.listFeedbackRequests.mockResolvedValueOnce({
      content: [{
        id: "request-id",
        feedbackId: null,
        transactionId: "transaction-id",
        customerId: "customer-id",
        status: "COMPLETED",
        scheduledAt: "2026-01-01T00:00:00Z",
        sentAt: null,
        deliveredAt: null,
        completedAt: "2026-01-02T00:00:00Z",
        expiresAt: "2026-02-01T00:00:00Z",
        createdAt: "2026-01-01T00:00:00Z",
        feedbackLink: null,
      }],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    });

    render(<FeedbackRequestsPage />);
    expect(await screen.findByText("Analysis not available yet")).toBeTruthy();
    expect(screen.queryByRole("link", { name: "View analysis" })).toBeNull();
  });
});
