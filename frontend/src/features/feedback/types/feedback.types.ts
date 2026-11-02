import type { PageResponse } from "../../../types/api";

export type FeedbackQuestionType = "TEXT" | "RATING" | "YES_NO";

export interface FeedbackQuestion {
  id: string;
  questionText: string;
  questionType: FeedbackQuestionType;
  required: boolean;
  active: boolean;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface FeedbackQuestionInput {
  questionText: string;
  questionType: FeedbackQuestionType;
  required: boolean;
  displayOrder: number;
  active: boolean;
}

export type FeedbackQuestionPage = PageResponse<FeedbackQuestion>;
export type FeedbackRequestStatus =
  | "PENDING"
  | "SCHEDULED"
  | "SENT"
  | "DELIVERED"
  | "FAILED"
  | "COMPLETED"
  | "EXPIRED"
  | "CANCELLED";

export interface FeedbackRequest {
  id: string;
  feedbackId: string | null;
  transactionId: string;
  customerId: string;
  status: FeedbackRequestStatus;
  scheduledAt: string;
  sentAt: string | null;
  deliveredAt: string | null;
  completedAt: string | null;
  expiresAt: string;
  createdAt: string;
  feedbackLink: string | null;
}

export type FeedbackRequestPage = PageResponse<FeedbackRequest>;

export interface CreateFeedbackRequestInput {
  transactionId: string;
  scheduledAt?: string;
}

export interface PublicFeedbackQuestion {
  id: string;
  questionText: string;
  questionType: FeedbackQuestionType;
  required: boolean;
  active: boolean;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface PublicFeedbackPage {
  businessName: string;
  expiresAt: string;
  questions: PublicFeedbackQuestion[];
  completion: FeedbackConfirmation | null;
}

export type ConversationStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "ABANDONED"
  | "EXPIRED";

export type ConversationSender = "CUSTOMER" | "SYSTEM" | "AI";
export type ConversationMessageType =
  | "TEXT"
  | "QUESTION"
  | "ANSWER"
  | "RATING"
  | "SYSTEM_EVENT";

export interface ConversationMessage {
  senderType: ConversationSender;
  messageType: ConversationMessageType;
  content: string;
  createdAt: string;
}

export interface FeedbackConversation {
  status: ConversationStatus;
  startedAt: string;
  completedAt: string | null;
  messages: ConversationMessage[];
  questions: PublicFeedbackQuestion[];
}

export interface FeedbackAnswerInput {
  questionId: string;
  answerText?: string;
  answerRating?: number;
  answerBoolean?: boolean;
}

export interface FeedbackSubmissionInput {
  rating: number;
  comment?: string;
  answers: FeedbackAnswerInput[];
}

export interface FeedbackConfirmation {
  received: boolean;
  message: string;
  feedbackCompleted: boolean;
  conversationCompleted: boolean;
  completionMessage: string;
  googleReviewAvailable: boolean;
  googleReviewUrl: string | null;
  reviewInvitationMessage: string | null;
}

export interface FeedbackAnalysisTopic {
  name: string | null;
  sentiment: string | null;
  evidence: string | null;
}

export interface FeedbackAnalysisAspect {
  name: string | null;
  sentiment: string | null;
  explanation: string | null;
  evidence: string | null;
}

export interface FeedbackAnalysisIssue {
  name: string | null;
  description: string | null;
  severity: string | null;
  topic: string | null;
  evidence: string | null;
}

export interface FeedbackAnalysis {
  sentiment: string | null;
  sentimentConfidence: number | null;
  sentimentSummary: string | null;
  topics: FeedbackAnalysisTopic[];
  aspects: FeedbackAnalysisAspect[];
  issues: FeedbackAnalysisIssue[];
  positiveHighlights: string[];
  negativeHighlights: string[];
  improvementOpportunities: string[];
}

export interface FeedbackAnalysisStatus {
  feedbackId: string;
  status: string;
  analysis: FeedbackAnalysis | null;
  safeError: string | null;
  analyzedAt: string | null;
}
