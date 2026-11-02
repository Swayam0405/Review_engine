"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import type { ReactNode } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import { generateDashboardSummary, getDashboard } from "../services/dashboard.service";
import type {
  DashboardData,
  DashboardDateRange,
  DashboardMetric,
} from "../types/dashboard.types";
import styles from "./dashboard.module.scss";

type DashboardState =
  | { status: "loading" }
  | { status: "ready"; data: DashboardData }
  | { status: "error"; message: string };

function utcRange(startDate: string, endDate: string): DashboardDateRange {
  if (!startDate && !endDate) return {};
  if (!startDate || !endDate) {
    throw new Error("Choose both a start date and an end date.");
  }
  const start = new Date(`${startDate}T00:00:00.000Z`);
  const end = new Date(`${endDate}T00:00:00.000Z`);
  const endExclusive = new Date(end.getTime() + 24 * 60 * 60 * 1000);
  const days = (endExclusive.getTime() - start.getTime()) / 86_400_000;
  if (!Number.isFinite(days) || days < 1 || days > 366) {
    throw new Error("Choose a date range of 1 to 366 days.");
  }
  return {
    from: start.toISOString(),
    to: endExclusive.toISOString(),
  };
}

function formatPeriod(value: string): string {
  return new Date(value).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

function formatCount(value: number): string {
  return new Intl.NumberFormat().format(value);
}

function sentimentName(value: string): string {
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

function MetricCard({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <article className={styles.metricCard}>
      <p className={styles.metricLabel}>{label}</p>
      <p className={styles.metricValue}>{value}</p>
      {hint ? <p className={styles.metricHint}>{hint}</p> : null}
    </article>
  );
}

function Distribution({
  title,
  values,
}: {
  title: string;
  values: Record<string, DashboardMetric | number>;
}) {
  const entries = Object.entries(values);
  return (
    <section className={styles.section} aria-labelledby={`${title}-title`}>
      <h2 className={styles.sectionTitle} id={`${title}-title`}>{title}</h2>
      {entries.length === 0 ? (
        <p className={styles.empty}>No {title.toLowerCase()} data for this period.</p>
      ) : (
        <ul className={styles.dataList}>
          {entries.map(([key, metric]) => {
            const count = typeof metric === "number" ? metric : metric.count;
            const percentage =
              typeof metric === "number" ? null : metric.percentage;
            return (
              <li className={styles.distributionRow} key={key}>
                <span>{sentimentName(key)}</span>
                <strong>{formatCount(count)}</strong>
                {percentage !== null ? <span>{percentage.toFixed(1)}%</span> : null}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

function TrendChart({ data }: { data: DashboardData }) {
  const maxCount = Math.max(1, ...data.trends.points.map((point) => point.feedbackCount));
  return (
    <section className={styles.section} aria-labelledby="trends-title">
      <h2 className={styles.sectionTitle} id="trends-title">Feedback trends</h2>
      <p className={styles.sectionDescription}>
        Weekly UTC buckets; bars show completed feedback count. Counts and
        average ratings are returned by the backend.
      </p>
      {data.trends.points.length === 0 ? (
        <p className={styles.empty}>No trend data for this period.</p>
      ) : (
        <div
          className={styles.trendList}
          role="img"
          aria-label="Weekly completed feedback counts and average ratings"
        >
          {data.trends.points.map((point) => (
            <div className={styles.trendRow} key={point.periodStart}>
              <time className={styles.trendDate} dateTime={point.periodStart}>
                {formatPeriod(point.periodStart)}
              </time>
              <div
                className={styles.trendTrack}
                aria-label={`${point.feedbackCount} feedback`}
              >
                <span
                  className={styles.trendBar}
                  style={{ width: `${(point.feedbackCount / maxCount) * 100}%` }}
                />
              </div>
              <span className={styles.trendCount}>
                {formatCount(point.feedbackCount)}
              </span>
              <span className={styles.trendRating}>
                {point.feedbackCount > 0
                  ? `Avg. ${point.averageRating.toFixed(1)}`
                  : "No ratings"}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function InsightList<T>({
  title,
  items,
  empty,
  render,
}: {
  title: string;
  items: T[];
  empty: string;
  render: (item: T, index: number) => ReactNode;
}) {
  const headingId = `${title.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-title`;
  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <h2 className={styles.sectionTitle} id={headingId}>{title}</h2>
      {items.length === 0 ? (
        <p className={styles.empty}>{empty}</p>
      ) : (
        <ul className={styles.insightList}>
          {items.map((item, index) => (
            <li className={styles.insightCard} key={`${headingId}-${index}`}>
              {render(item, index)}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function SummaryContent({
  data,
  generating,
  generationError,
  generationSuccess,
  onGenerate,
}: {
  data: DashboardData;
  generating: boolean;
  generationError: string | null;
  generationSuccess: string | null;
  onGenerate: () => void;
}) {
  const summary = data.summary;
  const canGenerate = data.overview.analysisCoverage.analyzed >= 3;
  const action = (
    <>
      <button
        className={styles.secondaryButton}
        type="button"
        disabled={!canGenerate || generating}
        onClick={onGenerate}
      >
        {generating
          ? "Generating summary…"
          : summary
            ? "Refresh summary"
            : "Generate summary"}
      </button>
      <p className={styles.sectionDescription}>
        The backend requires at least three completed feedback analyses in the selected period.
      </p>
      {generationError ? <p className={styles.error} role="alert">{generationError}</p> : null}
      {generationSuccess ? <p className={styles.success} role="status">{generationSuccess}</p> : null}
    </>
  );
  if (!summary) {
    return (
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Business summary</h2>
        {action}
        <p className={styles.empty}>
          No saved summary is available for this period. Dashboard metrics
          remain available independently. {canGenerate ? "" : "At least three analyzed feedback records are needed before generating a summary."}
        </p>
      </section>
    );
  }
  return (
    <section className={styles.section} aria-labelledby="summary-title">
      <h2 className={styles.sectionTitle} id="summary-title">AI-generated business summary</h2>
      {action}
      <p className={styles.disclaimer}>
        An interpretation of analyzed feedback, not verified business facts.
      </p>
      <p className={styles.summaryText}>{summary.summary}</p>
      <div className={styles.summaryMeta}>
        <span>Based on {formatCount(summary.feedbackCountUsed)} feedback records</span>
        <span>Generated {formatPeriod(summary.generatedAt)}</span>
      </div>
      <div className={styles.insightGrid}>
        <TextInsights title="Positive observations" items={summary.positiveObservations} />
        <TextInsights title="Main concerns" items={summary.mainConcerns} />
        <TextInsights title="Recurring issues" items={summary.recurringIssues} />
        <TextInsights title="Recommendations" items={summary.improvementRecommendations} />
      </div>
    </section>
  );
}

function TextInsights({ title, items }: { title: string; items: string[] }) {
  return (
    <div className={styles.summaryBlock}>
      <h3>{title}</h3>
      {items.length > 0 ? (
        <ul className={styles.textList}>
          {items.map((item, index) => <li key={`${title}-${index}`}>{item}</li>)}
        </ul>
      ) : (
        <p className={styles.empty}>No items returned.</p>
      )}
    </div>
  );
}

function DashboardContent() {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [appliedRange, setAppliedRange] = useState<DashboardDateRange>({});
  const [rangeError, setRangeError] = useState<string | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [summaryGenerating, setSummaryGenerating] = useState(false);
  const [summaryGenerationError, setSummaryGenerationError] = useState<string | null>(null);
  const [summaryGenerationSuccess, setSummaryGenerationSuccess] = useState<string | null>(null);
  const summaryRequestInFlight = useRef(false);
  const [loadResult, setLoadResult] = useState<{
    key: string;
    state: Exclude<DashboardState, { status: "loading" }>;
  } | null>(null);
  const key = `${appliedRange.from ?? ""}:${appliedRange.to ?? ""}:${refresh}`;

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void getDashboard(appliedRange, controller.signal)
      .then((data) => {
        if (active) setLoadResult({ key, state: { status: "ready", data } });
      })
      .catch((cause: unknown) => {
        if (!active || controller.signal.aborted) return;
        setLoadResult({
          key,
          state: {
            status: "error",
            message:
              cause instanceof ApiError
                ? cause.message
                : "Dashboard analytics could not be loaded.",
          },
        });
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [appliedRange, key]);

  const state =
    loadResult?.key === key
      ? loadResult.state
      : ({ status: "loading" } as const);

  function applyDateRange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      const range = utcRange(startDate, endDate);
      setRangeError(null);
      setAppliedRange(range);
    } catch (cause) {
      setRangeError(
        cause instanceof Error ? cause.message : "The date range is invalid.",
      );
    }
  }

  async function generateSummary() {
    if (
      summaryRequestInFlight.current ||
      state.status !== "ready" ||
      state.data.overview.analysisCoverage.analyzed < 3
    ) {
      return;
    }
    summaryRequestInFlight.current = true;
    setSummaryGenerating(true);
    setSummaryGenerationError(null);
    setSummaryGenerationSuccess(null);
    try {
      const summary = await generateDashboardSummary(appliedRange);
      setLoadResult((current) =>
        current?.key === key && current.state.status === "ready"
          ? {
              ...current,
              state: {
                ...current.state,
                data: { ...current.state.data, summary },
              },
            }
          : current,
      );
      setSummaryGenerationSuccess("The backend summary is up to date.");
    } catch (cause) {
      setSummaryGenerationError(
        cause instanceof ApiError
          ? cause.message
          : "The backend summary could not be generated.",
      );
    } finally {
      summaryRequestInFlight.current = false;
      setSummaryGenerating(false);
    }
  }

  const overview = state.status === "ready" ? state.data.overview : null;
  const metrics = overview?.feedbackMetrics;
  const sentimentValues = overview
    ? Object.fromEntries(
        Object.entries(overview.sentimentDistribution).map(([name, value]) => [
          name,
          value,
        ]),
      )
    : {};

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Review Engine</p>
            <h1 className={styles.title}>Business dashboard</h1>
            <p className={styles.description}>
              Business-scoped feedback and analysis metrics from your backend.
            </p>
          </div>
          <button
            className={styles.secondaryButton}
            type="button"
            onClick={() => setRefresh((value) => value + 1)}
          >
            Refresh dashboard
          </button>
        </header>
        <form className={styles.filter} onSubmit={applyDateRange}>
          <p className={styles.filterHelp}>
            Optional date filters use UTC calendar days. Leave both blank for
            the backend default period. The selected end date is included.
          </p>
          <label className={styles.field}>
            <span>From (UTC)</span>
            <input
              type="date"
              value={startDate}
              onChange={(event) => setStartDate(event.target.value)}
            />
          </label>
          <label className={styles.field}>
            <span>Through (UTC)</span>
            <input
              type="date"
              value={endDate}
              onChange={(event) => setEndDate(event.target.value)}
            />
          </label>
          <button className={styles.button} type="submit">Apply date range</button>
          {rangeError ? <p className={styles.error} role="alert">{rangeError}</p> : null}
        </form>

        {state.status === "loading" ? (
          <div className={styles.metricGrid} role="status" aria-label="Loading dashboard">
            {Array.from({ length: 6 }, (_, index) => (
              <div className={styles.skeleton} key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <section className={styles.section}>
            <p className={styles.error} role="alert">{state.message}</p>
            <button
              className={styles.secondaryButton}
              type="button"
              onClick={() => setRefresh((value) => value + 1)}
            >
              Retry dashboard
            </button>
          </section>
        ) : (
          <div className={styles.stack}>
            <div className={styles.period}>
              Reporting period: {formatPeriod(state.data.overview.period.from)} –{" "}
              {formatPeriod(state.data.overview.period.to)} (end exclusive)
            </div>
            <section aria-label="Feedback summary metrics">
              <div className={styles.metricGrid}>
                <MetricCard
                  label="Completed feedback"
                  value={formatCount(metrics!.totalCompletedFeedback)}
                />
                <MetricCard
                  label="Average rating"
                  value={
                    metrics!.totalCompletedFeedback > 0
                      ? `${metrics!.averageRating.toFixed(1)} / 5`
                      : "—"
                  }
                  hint={metrics!.totalCompletedFeedback === 0 ? "No feedback in this period" : undefined}
                />
                <MetricCard
                  label="Analyzed"
                  value={formatCount(state.data.overview.analysisCoverage.analyzed)}
                  hint={`${state.data.overview.analysisCoverage.analyzedPercentage.toFixed(1)}% of feedback`}
                />
                <MetricCard
                  label="Analysis pending"
                  value={formatCount(state.data.overview.analysisCoverage.pending)}
                />
                <MetricCard
                  label="Analysis failed"
                  value={formatCount(state.data.overview.analysisCoverage.failed)}
                />
                <MetricCard
                  label="Feedback change"
                  value={
                    state.data.overview.comparison?.percentageChange === null ||
                    state.data.overview.comparison === null
                      ? "—"
                      : `${state.data.overview.comparison.percentageChange.toFixed(1)}%`
                  }
                  hint={
                    state.data.overview.comparison
                      ? "Compared with the preceding equal-length period"
                      : "No previous-period comparison available"
                  }
                />
              </div>
            </section>

            <div className={styles.twoColumn}>
              <Distribution
                title="Rating distribution"
                values={state.data.overview.ratingDistribution}
              />
              <Distribution title="Sentiment distribution" values={sentimentValues} />
            </div>

            <TrendChart data={state.data} />

            <div className={styles.twoColumn}>
              <InsightList
                title="Common topics"
                items={state.data.topics}
                empty="No analyzed topics were returned for this period."
                render={(topic) => (
                  <>
                    <div className={styles.insightTitle}>
                      <strong>{topic.topic}</strong>
                      <span>{formatCount(topic.feedbackCount)} feedback records</span>
                    </div>
                    <p className={styles.muted}>
                      {topic.analyzedFeedbackPercentage.toFixed(1)}% of analyzed
                      feedback mentions this topic.
                    </p>
                  </>
                )}
              />
              <InsightList
                title="Business aspects"
                items={state.data.aspects}
                empty="No analyzed aspects were returned for this period."
                render={(aspect) => (
                  <>
                    <div className={styles.insightTitle}>
                      <strong>{aspect.aspect}</strong>
                      <span>{formatCount(aspect.mentions)} mentions</span>
                    </div>
                    <p className={styles.muted}>
                      Model self-assessed mean confidence:{" "}
                      {(aspect.averageSentimentConfidence * 100).toFixed(1)}%
                    </p>
                    <small>{Object.entries(aspect.sentimentDistribution)
                      .map(([name, count]) => `${sentimentName(name)} ${count}`)
                      .join(" · ")}</small>
                  </>
                )}
              />
            </div>

            <InsightList
              title="Recurring customer issues"
              items={state.data.issues}
              empty="No recurring issues were returned for this period."
              render={(issue) => (
                <>
                  <div className={styles.insightTitle}>
                    <strong>{issue.issue}</strong>
                    <span>{formatCount(issue.distinctFeedbackReports)} reports</span>
                  </div>
                  <p>{issue.description}</p>
                  {issue.relatedTopics ? <p className={styles.muted}>Related topics: {issue.relatedTopics}</p> : null}
                  <small>
                    Severity distribution:{" "}
                    {Object.entries(issue.severityDistribution)
                      .map(([level, count]) => `${level} ${count}`)
                      .join(" · ")}
                  </small>
                </>
              )}
            />

            <div className={styles.twoColumn}>
              <InsightList
                title="Positive highlights"
                items={state.data.highlights.positive}
                empty="No positive highlights were returned."
                render={(highlight) => (
                  <>
                    <strong>{highlight.topicOrAspect}</strong>
                    <p>{highlight.explanation}</p>
                    <small>Supported by {formatCount(highlight.supportingFeedbackRecords)} feedback records</small>
                  </>
                )}
              />
              <InsightList
                title="Negative highlights"
                items={state.data.highlights.negative}
                empty="No negative highlights were returned."
                render={(highlight) => (
                  <>
                    <strong>{highlight.topicOrAspect}</strong>
                    <p>{highlight.explanation}</p>
                    <small>Supported by {formatCount(highlight.supportingFeedbackRecords)} feedback records</small>
                  </>
                )}
              />
            </div>

            <SummaryContent
              data={state.data}
              generating={summaryGenerating}
              generationError={summaryGenerationError}
              generationSuccess={summaryGenerationSuccess}
              onGenerate={() => void generateSummary()}
            />

            <section className={styles.section} aria-labelledby="activity-title">
              <h2 className={styles.sectionTitle} id="activity-title">Feedback activity</h2>
              <p className={styles.sectionDescription}>
                Request/conversation counts use lifecycle and creation-time
                rules from the backend; analysis counts use feedback submission time.
              </p>
              <div className={styles.activityGrid}>
                {[
                  ["Requests", state.data.activity.totalRequests],
                  ["Pending requests", state.data.activity.pendingRequests],
                  ["Scheduled requests", state.data.activity.scheduledRequests],
                  ["Sent requests", state.data.activity.sentRequests],
                  ["Delivered requests", state.data.activity.deliveredRequests],
                  ["Failed requests", state.data.activity.failedRequests],
                  ["Completed requests", state.data.activity.completedRequests],
                  ["Expired requests", state.data.activity.expiredRequests],
                  ["Conversations in progress", state.data.activity.conversationsInProgress],
                  ["Completed conversations", state.data.activity.conversationsCompleted],
                  ["Analysis pending", state.data.activity.analysisPending],
                  ["Analysis failed", state.data.activity.analysisFailed],
                ].map(([label, count]) => (
                  <div className={styles.activityItem} key={label}>
                    <span>{label}</span>
                    <strong>{formatCount(Number(count))}</strong>
                  </div>
                ))}
              </div>
            </section>
            <p className={styles.disclaimer}>
              Topics, aspects, issues, highlights, sentiment and summary are
              backend-generated interpretations of feedback. They are distinct
              from customer-provided statements. Google reviews are not included
              in private feedback metrics.
            </p>
          </div>
        )}
      </div>
    </main>
  );
}

export function DashboardPage() {
  return (
    <BusinessRouteGate>
      <DashboardContent />
    </BusinessRouteGate>
  );
}
