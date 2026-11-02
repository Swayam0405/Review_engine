"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../../lib/api/api-error";
import {
  sendConversationMessage,
  startOrResumeConversation,
  submitFeedback,
  validateFeedbackLink,
} from "../services/public-feedback.service";
import {
  buildFeedbackAnswers,
  validateFeedbackSubmission,
} from "../schemas/feedback.schemas";
import type {
  FeedbackConfirmation,
  FeedbackConversation,
  PublicFeedbackPage as PublicFeedbackData,
} from "../types/feedback.types";
import styles from "./feedback.module.scss";

type PublicPageState =
  | { status: "loading" }
  | { status: "ready"; data: PublicFeedbackData; conversation: FeedbackConversation }
  | { status: "completed"; data: PublicFeedbackData; confirmation: FeedbackConfirmation }
  | { status: "error"; message: string; kind: "expired" | "unavailable" | "general" };

function publicError(cause: unknown): Extract<PublicPageState, { status: "error" }> {
  if (cause instanceof ApiError && cause.status === 410) {
    return {
      status: "error",
      kind: "expired",
      message: "This feedback link has expired. Please contact the business for a new link.",
    };
  }
  if (cause instanceof ApiError && cause.status === 404) {
    return {
      status: "error",
      kind: "unavailable",
      message: "This feedback link is unavailable or has already been used.",
    };
  }
  return {
    status: "error",
    kind: "general",
    message:
      cause instanceof ApiError
        ? cause.message
        : "Feedback is temporarily unavailable. Please try again later.",
  };
}

function RatingControl({
  label,
  value,
  onChange,
  error,
  required = true,
  errorId = "rating-error",
}: {
  label: string;
  value: number | null;
  onChange: (rating: number) => void;
  error?: string;
  required?: boolean;
  errorId?: string;
}) {
  return (
    <fieldset className={styles.ratingGroup} aria-describedby={error ? errorId : undefined}>
      <legend className={styles.label}>{label}{required ? " *" : ""}</legend>
      <div className={styles.ratingOptions}>
        {[1, 2, 3, 4, 5].map((rating) => (
          <button
            className={`${styles.rating} ${value === rating ? styles.ratingSelected : ""}`}
            type="button"
            key={rating}
            aria-pressed={value === rating}
            onClick={() => onChange(rating)}
          >
            {rating}
          </button>
        ))}
      </div>
      {error ? <span className={styles.fieldError} id={errorId}>{error}</span> : null}
    </fieldset>
  );
}

function PublicFeedbackContent({ token }: { token: string }) {
  const [retry, setRetry] = useState(0);
  const loadKey = `${token}:${retry}`;
  const [loadResult, setLoadResult] = useState<{
    key: string;
    result: PublicPageState;
  } | null>(null);
  const [message, setMessage] = useState("");
  const [messageRating, setMessageRating] = useState<number | null>(null);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const messageInFlight = useRef(false);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [rating, setRating] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const [submissionErrors, setSubmissionErrors] = useState<Record<string, string>>({});
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submissionInFlight = useRef(false);
  const [submissionUnknown, setSubmissionUnknown] = useState(false);
  const [recoveryChecking, setRecoveryChecking] = useState(false);
  const [confirmation, setConfirmation] = useState<FeedbackConfirmation | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void validateFeedbackLink(token, controller.signal)
      .then(async (data) => {
        if (data.completion?.feedbackCompleted && data.completion.conversationCompleted) {
          if (active) {
            setLoadResult({
              key: loadKey,
              result: { status: "completed", data, confirmation: data.completion },
            });
          }
          return;
        }
        const conversation = await startOrResumeConversation(token);
        if (active) {
          setLoadResult({
            key: loadKey,
            result: { status: "ready", data, conversation },
          });
        }
      })
      .catch((cause: unknown) => {
        if (active && !controller.signal.aborted) {
          setLoadResult({ key: loadKey, result: publicError(cause) });
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [loadKey, token]);

  const state =
    loadResult?.key === loadKey
      ? loadResult.result
      : ({ status: "loading" } as const);

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (messageInFlight.current || !message.trim()) return;
    messageInFlight.current = true;
    setSending(true);
    setSendError(null);
    try {
      const conversation = await sendConversationMessage(token, {
        content: message.trim(),
        ...(messageRating !== null ? { rating: messageRating } : {}),
      });
      setLoadResult((current) =>
        current?.key === loadKey && current.result.status === "ready"
          ? {
              ...current,
              result: { ...current.result, conversation },
            }
          : current,
      );
      setMessage("");
      setMessageRating(null);
    } catch (cause) {
      setSendError(
        cause instanceof ApiError
          ? cause.message
          : "Your message could not be sent. Please try again.",
      );
    } finally {
      messageInFlight.current = false;
      setSending(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (
      submissionInFlight.current ||
      submissionUnknown ||
      (confirmation?.feedbackCompleted && confirmation.conversationCompleted)
    ) {
      return;
    }
    const result = buildFeedbackAnswers(
      state.status === "ready" ? state.data.questions : [],
      answers,
    );
    const errors = {
      ...result.errors,
      ...validateFeedbackSubmission({ rating, comment }),
    };
    setSubmissionErrors(errors);
    setSubmissionError(null);
    if (Object.keys(errors).length || rating === null) return;

    submissionInFlight.current = true;
    setSubmitting(true);
    try {
      const response = await submitFeedback(token, {
        rating,
        ...(comment.trim() ? { comment: comment.trim() } : {}),
        answers: result.answers,
      });
      if (response.feedbackCompleted && response.conversationCompleted) {
        setConfirmation(response);
      } else {
        await recoverSubmissionStatus();
      }
    } catch (cause) {
      if (
        !(cause instanceof ApiError) ||
        cause.code === "NETWORK_ERROR" ||
        cause.code === "TIMEOUT" ||
        (cause instanceof ApiError &&
          ((cause.status ?? 0) >= 500 || cause.status === 404 || cause.status === 409 || cause.status === 410))
      ) {
        await recoverSubmissionStatus();
      } else {
        setSubmissionError(
          cause instanceof ApiError
            ? cause.message
            : "Your feedback could not be submitted.",
        );
      }
    } finally {
      submissionInFlight.current = false;
      setSubmitting(false);
    }
  }

  async function recoverSubmissionStatus() {
    setSubmissionUnknown(true);
    setRecoveryChecking(true);
    setSubmissionError("Checking whether the backend recorded your feedback…");
    try {
      const current = await validateFeedbackLink(token);
      if (current.completion?.feedbackCompleted && current.completion.conversationCompleted) {
        setConfirmation(current.completion);
        setSubmissionUnknown(false);
        setSubmissionError(null);
      } else {
        setSubmissionUnknown(false);
        setSubmissionError(
          "The backend did not mark your feedback as complete. You can review it and choose Submit feedback to try again.",
        );
      }
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 410) {
        setSubmissionError(
          "This feedback link has expired, so the backend cannot confirm whether an earlier submission completed. Do not submit again; contact the business for help.",
        );
      } else {
        setSubmissionError(
          cause instanceof ApiError
            ? `We could not check the submission status: ${cause.message} Do not submit again until the status check succeeds.`
            : "We could not check the submission status. Do not submit again until the status check succeeds.",
        );
      }
    } finally {
      setRecoveryChecking(false);
    }
  }

  if (state.status === "loading") {
    return (
      <main className={styles.publicPage}>
        <section className={`${styles.publicContent} ${styles.card}`} role="status">
          <div className={styles.skeleton} aria-hidden="true" />
          <p>Loading your feedback page…</p>
        </section>
      </main>
    );
  }

  if (state.status === "error") {
    return (
      <main className={styles.publicPage}>
        <section className={`${styles.publicContent} ${styles.stateCard}`}>
          <h1 className={styles.title}>
            {state.kind === "expired" ? "Feedback link expired" : "Feedback unavailable"}
          </h1>
          <p className={state.kind === "general" ? styles.error : styles.notice} role="alert">
            {state.message}
          </p>
          {state.kind === "general" ? (
            <button
              className={styles.secondaryButton}
              type="button"
              onClick={() => setRetry((value) => value + 1)}
            >
              Try again
            </button>
          ) : null}
        </section>
      </main>
    );
  }

  if (state.status === "completed") {
    return (
      <main className={styles.publicPage}>
        <div className={`${styles.publicContent} ${styles.stack}`}>
          <header className={styles.header}>
            <div>
              <p className={styles.eyebrow}>Customer feedback</p>
              <h1 className={styles.title}>{state.data.businessName}</h1>
            </div>
          </header>
          <section className={styles.card}>
            <h2 className={styles.rowTitle}>Thank you for your feedback</h2>
            <p className={styles.success} role="status">
              {state.confirmation.completionMessage}
            </p>
            {state.confirmation.googleReviewAvailable && state.confirmation.googleReviewUrl ? (
              <section className={styles.stack}>
                <p>
                  {state.confirmation.reviewInvitationMessage ??
                    "You may also leave a Google review if you choose."}
                </p>
                <a
                  className={styles.button}
                  href={state.confirmation.googleReviewUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  Open Google review page
                </a>
              </section>
            ) : null}
          </section>
        </div>
      </main>
    );
  }

  const completed = Boolean(
    confirmation?.feedbackCompleted && confirmation.conversationCompleted,
  );

  return (
    <main className={styles.publicPage}>
      <div className={`${styles.publicContent} ${styles.stack}`}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Customer feedback</p>
            <h1 className={styles.title}>{state.data.businessName}</h1>
            <p className={styles.description}>
              Share your experience. Your feedback is sent securely to the business.
            </p>
          </div>
        </header>
        {completed && confirmation ? (
          <section className={styles.card}>
            <h2 className={styles.rowTitle}>Thank you for your feedback</h2>
            <p className={styles.success} role="status">
              {confirmation.completionMessage}
            </p>
            {confirmation.googleReviewAvailable && confirmation.googleReviewUrl ? (
              <section className={styles.stack}>
                <p>{confirmation.reviewInvitationMessage ?? "You may also leave a Google review."}</p>
                <a
                  className={styles.button}
                  href={confirmation.googleReviewUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  Open Google review page
                </a>
              </section>
            ) : null}
          </section>
        ) : (
          <>
            <section className={styles.card} aria-labelledby="conversation-title">
              <h2 className={styles.rowTitle} id="conversation-title">Your conversation</h2>
              <ul className={styles.messages} aria-live="polite">
                {state.conversation.messages.map((entry, index) => (
                  <li
                    className={`${styles.message} ${entry.senderType === "CUSTOMER" ? styles.customerMessage : styles.systemMessage}`}
                    key={`${entry.createdAt}-${index}`}
                  >
                    <span className={styles.srOnly}>
                      {entry.senderType === "CUSTOMER" ? "You" : state.data.businessName}:
                    </span>
                    {entry.content}
                  </li>
                ))}
                {state.conversation.messages.length === 0 ? (
                  <li className={styles.muted}>Start by telling us about your experience.</li>
                ) : null}
              </ul>
              <form className={styles.stack} onSubmit={sendMessage}>
                {sendError ? <p className={styles.error} role="alert">{sendError}</p> : null}
                <label className={styles.field}>
                  <span className={styles.label}>Message</span>
                  <textarea
                    className={styles.textarea}
                    value={message}
                    maxLength={10000}
                    required
                    onChange={(event) => setMessage(event.target.value)}
                  />
                </label>
                <RatingControl
                  label="Optional conversation rating"
                  value={messageRating}
                  onChange={setMessageRating}
                  required={false}
                />
                <button className={styles.secondaryButton} type="submit" disabled={sending}>
                  {sending ? "Sending…" : "Send message"}
                </button>
              </form>
            </section>
            <section className={styles.card} aria-labelledby="submit-title">
              <h2 className={styles.rowTitle} id="submit-title">Submit your feedback</h2>
              {submissionError ? (
                <p className={submissionUnknown ? styles.notice : styles.error} role="alert">
                  {submissionError}
                </p>
              ) : null}
              {submissionUnknown ? (
                <button
                  className={styles.secondaryButton}
                  type="button"
                  disabled={recoveryChecking}
                  onClick={() => void recoverSubmissionStatus()}
                >
                  {recoveryChecking ? "Checking status…" : "Check submission status"}
                </button>
              ) : null}
              <form className={styles.stack} onSubmit={submit} noValidate>
                {state.data.questions.map((question) => (
                  <label className={styles.field} key={question.id}>
                    <span className={styles.label}>
                      {question.questionText}{question.required ? " *" : " (optional)"}
                    </span>
                    {question.questionType === "TEXT" ? (
                      <textarea
                        className={styles.textarea}
                        value={answers[question.id] ?? ""}
                        maxLength={5000}
                        required={question.required}
                        aria-invalid={Boolean(submissionErrors[question.id])}
                        onChange={(event) =>
                          setAnswers((current) => ({
                            ...current,
                            [question.id]: event.target.value,
                          }))
                        }
                      />
                    ) : question.questionType === "RATING" ? (
                      <select
                        className={styles.select}
                        value={answers[question.id] ?? ""}
                        required={question.required}
                        aria-invalid={Boolean(submissionErrors[question.id])}
                        onChange={(event) =>
                          setAnswers((current) => ({
                            ...current,
                            [question.id]: event.target.value,
                          }))
                        }
                      >
                        <option value="">Select a rating</option>
                        {[1, 2, 3, 4, 5].map((value) => (
                          <option key={value} value={value}>{value}</option>
                        ))}
                      </select>
                    ) : (
                      <select
                        className={styles.select}
                        value={answers[question.id] ?? ""}
                        required={question.required}
                        aria-invalid={Boolean(submissionErrors[question.id])}
                        onChange={(event) =>
                          setAnswers((current) => ({
                            ...current,
                            [question.id]: event.target.value,
                          }))
                        }
                      >
                        <option value="">Select yes or no</option>
                        <option value="true">Yes</option>
                        <option value="false">No</option>
                      </select>
                    )}
                    {submissionErrors[question.id] ? (
                      <span className={styles.fieldError}>{submissionErrors[question.id]}</span>
                    ) : null}
                  </label>
                ))}
                <RatingControl
                  label="Overall rating"
                  value={rating}
                  error={submissionErrors.rating}
                  onChange={setRating}
                  errorId="overall-rating-error"
                />
                <label className={styles.field}>
                  <span className={styles.label}>Comment (optional)</span>
                  <textarea
                    className={styles.textarea}
                    value={comment}
                    maxLength={5000}
                    aria-invalid={Boolean(submissionErrors.comment)}
                    onChange={(event) => setComment(event.target.value)}
                  />
                  {submissionErrors.comment ? (
                    <span className={styles.fieldError}>{submissionErrors.comment}</span>
                  ) : null}
                </label>
                <button
                  className={styles.button}
                  type="submit"
                  disabled={submitting || submissionUnknown}
                >
                  {submitting
                    ? "Submitting…"
                    : submissionUnknown
                      ? "Submission status unknown"
                      : "Submit feedback"}
                </button>
              </form>
            </section>
          </>
        )}
      </div>
    </main>
  );
}

export function PublicFeedbackPage({ token }: { token: string }) {
  return <PublicFeedbackContent token={token} />;
}
