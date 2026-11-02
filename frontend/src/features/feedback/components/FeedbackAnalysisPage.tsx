"use client";

import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import {
  getFeedbackAnalysis,
  retryFeedbackAnalysis,
} from "../services/feedback-analysis.service";
import type {
  FeedbackAnalysisAspect,
  FeedbackAnalysisIssue,
  FeedbackAnalysisStatus,
  FeedbackAnalysisTopic,
} from "../types/feedback.types";
import styles from "./feedback.module.scss";

type AnalysisState =
  | { status: "loading" }
  | { status: "unavailable"; message: string }
  | { status: "error"; message: string }
  | { status: "ready"; analysis: FeedbackAnalysisStatus };

function sentimentLabel(value: string | null): string {
  if (!value) return "Not provided";
  switch (value) {
    case "POSITIVE":
      return "Positive";
    case "NEUTRAL":
      return "Neutral";
    case "NEGATIVE":
      return "Negative";
    case "MIXED":
      return "Mixed";
    default:
      return value;
  }
}

function AnalysisList<T>({
  title,
  items,
  emptyMessage,
  renderItem,
}: {
  title: string;
  items: T[];
  emptyMessage: string;
  renderItem: (item: T, index: number) => ReactNode;
}) {
  const headingId = `${title.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-heading`;
  return (
    <section className={styles.analysisSection} aria-labelledby={headingId}>
      <h2 className={styles.analysisHeading} id={headingId}>{title}</h2>
      {items.length > 0 ? (
        <ul className={styles.analysisList}>
          {items.map((item, index) => (
            <li className={styles.analysisItem} key={`${title}-${index}`}>
              {renderItem(item, index)}
            </li>
          ))}
        </ul>
      ) : (
        <p className={styles.muted}>{emptyMessage}</p>
      )}
    </section>
  );
}

function TopicItem({ item }: { item: FeedbackAnalysisTopic }) {
  return (
    <div className={styles.analysisItemContent}>
      <div className={styles.meta}>
        <strong>{item.name ?? "Topic"}</strong>
        {item.sentiment ? (
          <span className={styles.badge}>{sentimentLabel(item.sentiment)}</span>
        ) : null}
      </div>
      {item.evidence ? <p className={styles.evidence}>{item.evidence}</p> : null}
    </div>
  );
}

function AspectItem({ item }: { item: FeedbackAnalysisAspect }) {
  return (
    <div className={styles.analysisItemContent}>
      <div className={styles.meta}>
        <strong>{item.name ?? "Aspect"}</strong>
        {item.sentiment ? (
          <span className={styles.badge}>{sentimentLabel(item.sentiment)}</span>
        ) : null}
      </div>
      {item.explanation ? <p>{item.explanation}</p> : null}
      {item.evidence ? <p className={styles.evidence}>{item.evidence}</p> : null}
    </div>
  );
}

function IssueItem({ item }: { item: FeedbackAnalysisIssue }) {
  return (
    <div className={styles.analysisItemContent}>
      <div className={styles.meta}>
        <strong>{item.name ?? "Issue"}</strong>
        {item.severity ? <span className={styles.badge}>{item.severity}</span> : null}
      </div>
      {item.description ? <p>{item.description}</p> : null}
      {item.topic ? <p className={styles.muted}>Topic: {item.topic}</p> : null}
      {item.evidence ? <p className={styles.evidence}>{item.evidence}</p> : null}
    </div>
  );
}

function TextList({ title, items }: { title: string; items: string[] }) {
  return (
    <AnalysisList
      title={title}
      items={items}
      emptyMessage="No items were returned."
      renderItem={(item) => <p className={styles.analysisText}>{item}</p>}
    />
  );
}

function CompletedAnalysis({ value }: { value: FeedbackAnalysisStatus }) {
  const analysis = value.analysis;
  if (!analysis) {
    return (
      <section className={styles.card}>
        <p className={styles.notice} role="status">
          The backend reported a completed job, but did not return an analysis result.
        </p>
      </section>
    );
  }
  const sentimentAvailable = analysis.sentiment !== null;

  return (
    <div className={styles.stack}>
      <section className={styles.card} aria-labelledby="analysis-result-heading">
        <p className={styles.eyebrow}>AI-generated interpretation</p>
        <h2 className={styles.analysisHeading} id="analysis-result-heading">
          Individual feedback analysis
        </h2>
        <div className={styles.sentimentPanel}>
          <div>
            <span className={styles.muted}>Sentiment</span>
            <p className={styles.sentimentLabel}>
              {sentimentLabel(analysis.sentiment)}
            </p>
          </div>
          {analysis.sentimentConfidence !== null ? (
            <div>
              <span className={styles.muted}>Model self-assessed confidence</span>
              <p className={styles.sentimentLabel}>
                {(analysis.sentimentConfidence * 100).toFixed(1)}%
              </p>
            </div>
          ) : null}
        </div>
        {!sentimentAvailable ? (
          <p className={styles.muted}>The backend did not provide a sentiment classification.</p>
        ) : null}
        {analysis.sentimentSummary ? (
          <div className={styles.summary}>
            <h3 className={styles.analysisSubheading}>Summary</h3>
            <p>{analysis.sentimentSummary}</p>
          </div>
        ) : (
          <p className={styles.muted}>No summary was returned.</p>
        )}
        {value.analyzedAt ? (
          <p className={styles.muted}>
            Analyzed <time dateTime={value.analyzedAt}>
              {new Date(value.analyzedAt).toLocaleString()}
            </time>
          </p>
        ) : null}
      </section>

      <section className={styles.card}>
        <p className={styles.muted}>
          The sections below are AI-generated interpretations grounded in the
          feedback. They are not additional customer statements or verified
          business facts.
        </p>
        <div className={styles.analysisGrid}>
          <AnalysisList
            title="Topics"
            items={analysis.topics}
            emptyMessage="No topics were returned."
            renderItem={(item) => <TopicItem item={item} />}
          />
          <AnalysisList
            title="Aspects"
            items={analysis.aspects}
            emptyMessage="No aspects were returned."
            renderItem={(item) => <AspectItem item={item} />}
          />
          <AnalysisList
            title="Issues"
            items={analysis.issues}
            emptyMessage="No issues were returned."
            renderItem={(item) => <IssueItem item={item} />}
          />
          <TextList title="Positive highlights" items={analysis.positiveHighlights} />
          <TextList title="Negative highlights" items={analysis.negativeHighlights} />
          <TextList
            title="Improvement opportunities"
            items={analysis.improvementOpportunities}
          />
        </div>
      </section>
    </div>
  );
}

function AnalysisContent({ feedbackId }: { feedbackId: string }) {
  const [revision, setRevision] = useState(0);
  const [loadResult, setLoadResult] = useState<{
    key: string;
    result: AnalysisState;
  } | null>(null);
  const [retrying, setRetrying] = useState(false);
  const retryInFlight = useRef(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [retryAccepted, setRetryAccepted] = useState(false);
  const key = `${feedbackId}:${revision}`;

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void getFeedbackAnalysis(feedbackId, controller.signal)
      .then((analysis) => {
        if (active) {
          setLoadResult({
            key,
            result: { status: "ready", analysis },
          });
        }
      })
      .catch((cause: unknown) => {
        if (!active || controller.signal.aborted) return;
        if (cause instanceof ApiError && cause.status === 404) {
          setLoadResult({
            key,
            result: {
              status: "unavailable",
              message:
                "No feedback analysis is available for this identifier, or the feedback is not accessible to this business.",
            },
          });
          return;
        }
        setLoadResult({
          key,
          result: {
            status: "error",
            message:
              cause instanceof ApiError
                ? cause.message
                : "Feedback analysis could not be loaded.",
          },
        });
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [feedbackId, key]);

  const state =
    loadResult?.key === key
      ? loadResult.result
      : ({ status: "loading" } as const);
  const analysis = state.status === "ready" ? state.analysis : null;
  const canRetry =
    analysis?.status === "FAILED" && analysis.analysis === null;

  async function retryAnalysis() {
    if (retryInFlight.current || !canRetry) return;
    retryInFlight.current = true;
    setRetrying(true);
    setActionError(null);
    setRetryAccepted(false);
    try {
      await retryFeedbackAnalysis(feedbackId);
      setRetryAccepted(true);
      setRevision((value) => value + 1);
    } catch (cause) {
      setActionError(
        cause instanceof Error
          ? cause.message
          : "The analysis retry could not be queued.",
      );
    } finally {
      retryInFlight.current = false;
      setRetrying(false);
    }
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Feedback intelligence</p>
            <h1 className={styles.title}>Feedback analysis</h1>
            <p className={styles.description}>
              Backend-generated analysis for one feedback record.
            </p>
          </div>
          {state.status !== "loading" && state.status !== "unavailable" ? (
            <button
              className={styles.secondaryButton}
              type="button"
              onClick={() => {
                setActionError(null);
                setRevision((value) => value + 1);
              }}
            >
              Refresh status
            </button>
          ) : null}
        </header>
        {retryAccepted ? (
          <p className={styles.notice} role="status">
            Retry accepted. Analysis will run asynchronously; refresh the status
            if it is still queued or processing.
          </p>
        ) : null}
        {actionError ? <p className={styles.error} role="alert">{actionError}</p> : null}
        {state.status === "loading" ? (
          <section className={styles.card} role="status">
            <div className={styles.skeleton} aria-hidden="true" />
            <p>Loading analysis status…</p>
          </section>
        ) : state.status === "unavailable" ? (
          <section className={styles.card}>
            <h2 className={styles.analysisHeading}>Analysis unavailable</h2>
            <p className={styles.notice} role="status">{state.message}</p>
          </section>
        ) : state.status === "error" ? (
          <section className={styles.card}>
            <h2 className={styles.analysisHeading}>Analysis could not be loaded</h2>
            <p className={styles.error} role="alert">{state.message}</p>
            <button
              className={styles.secondaryButton}
              type="button"
              onClick={() => setRevision((value) => value + 1)}
            >
              Try again
            </button>
          </section>
        ) : state.analysis.status === "COMPLETED" ? (
          <CompletedAnalysis value={state.analysis} />
        ) : state.analysis.status === "FAILED" ? (
          <section className={styles.card}>
            <h2 className={styles.analysisHeading}>Analysis failed</h2>
            <p className={styles.error} role="status">
              {state.analysis.safeError ?? "The backend could not complete this analysis."}
            </p>
            {canRetry ? (
              <button
                className={styles.button}
                type="button"
                disabled={retrying}
                onClick={() => void retryAnalysis()}
              >
                {retrying ? "Queueing retry…" : "Retry analysis"}
              </button>
            ) : null}
          </section>
        ) : state.analysis.status === "PENDING" ||
          state.analysis.status === "PROCESSING" ? (
          <section className={styles.card}>
            <span className={styles.badge} role="status">
              {state.analysis.status === "PENDING" ? "Queued" : "Processing"}
            </span>
            <h2 className={styles.analysisHeading}>Analysis in progress</h2>
            <p className={styles.muted}>
              Analysis runs asynchronously after feedback is submitted. This
              page does not poll automatically.
            </p>
          </section>
        ) : (
          <section className={styles.card}>
            <h2 className={styles.analysisHeading}>Analysis result unavailable</h2>
            <p className={styles.notice} role="status">
              The backend returned an unrecognized status or a processed job
              without an analysis payload. No completed analysis is displayed.
            </p>
          </section>
        )}
      </div>
    </main>
  );
}

export function FeedbackAnalysisPage({ feedbackId }: { feedbackId: string }) {
  return (
    <BusinessRouteGate>
      <AnalysisContent feedbackId={feedbackId} />
    </BusinessRouteGate>
  );
}
