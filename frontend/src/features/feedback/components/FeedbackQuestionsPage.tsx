"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { BusinessRouteGate } from "../../business/components/BusinessRouteGate";
import {
  listQuestions,
  saveQuestion,
  setQuestionActive,
} from "../services/feedback-owner.service";
import { validateFeedbackQuestion } from "../schemas/feedback.schemas";
import type {
  FeedbackQuestion,
  FeedbackQuestionInput,
  FeedbackQuestionPage,
  FeedbackQuestionType,
} from "../types/feedback.types";
import styles from "./feedback.module.scss";

type QuestionListState =
  | { key: string; status: "loading" }
  | { key: string; status: "ready"; page: FeedbackQuestionPage }
  | { key: string; status: "error"; message: string };

const EMPTY_QUESTION: FeedbackQuestionInput = {
  questionText: "",
  questionType: "TEXT",
  required: false,
  displayOrder: 0,
  active: true,
};

function QuestionEditor({
  question,
  onSaved,
  onCancel,
}: {
  question?: FeedbackQuestion;
  onSaved: () => void;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<FeedbackQuestionInput>(
    question
      ? {
          questionText: question.questionText,
          questionType: question.questionType,
          required: question.required,
          displayOrder: question.displayOrder,
          active: question.active,
        }
      : EMPTY_QUESTION,
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current) return;
    const input = { ...values, questionText: values.questionText.trim() };
    const validation = validateFeedbackQuestion(input);
    setErrors(validation);
    setError(null);
    if (Object.keys(validation).length) return;

    inFlight.current = true;
    setSaving(true);
    try {
      await saveQuestion(input, question?.id);
      onSaved();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "The feedback question could not be saved.",
      );
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <form className={styles.stack} noValidate onSubmit={submit}>
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      <label className={styles.field}>
        <span className={styles.label}>Question text *</span>
        <textarea
          className={styles.textarea}
          value={values.questionText}
          maxLength={1000}
          required
          aria-invalid={Boolean(errors.questionText)}
          onChange={(event) =>
            setValues((current) => ({ ...current, questionText: event.target.value }))
          }
        />
        {errors.questionText ? (
          <span className={styles.fieldError}>{errors.questionText}</span>
        ) : null}
      </label>
      <label className={styles.field}>
        <span className={styles.label}>Answer type *</span>
        <select
          className={styles.select}
          value={values.questionType}
          onChange={(event) =>
            setValues((current) => ({
              ...current,
              questionType: event.target.value as FeedbackQuestionType,
            }))
          }
        >
          <option value="TEXT">Text</option>
          <option value="RATING">Rating (1–5)</option>
          <option value="YES_NO">Yes / No</option>
        </select>
      </label>
      <label className={styles.field}>
        <span className={styles.label}>Display order *</span>
        <input
          className={styles.input}
          type="number"
          min={0}
          step={1}
          value={values.displayOrder}
          aria-invalid={Boolean(errors.displayOrder)}
          onChange={(event) =>
            setValues((current) => ({
              ...current,
              displayOrder:
                event.target.value === ""
                  ? Number.NaN
                  : Number(event.target.value),
            }))
          }
        />
        {errors.displayOrder ? (
          <span className={styles.fieldError}>{errors.displayOrder}</span>
        ) : null}
      </label>
      <label>
        <input
          type="checkbox"
          checked={values.required}
          onChange={(event) =>
            setValues((current) => ({ ...current, required: event.target.checked }))
          }
        />{" "}
        Required
      </label>
      <label>
        <input
          type="checkbox"
          checked={values.active}
          onChange={(event) =>
            setValues((current) => ({ ...current, active: event.target.checked }))
          }
        />{" "}
        Active
      </label>
      <div className={styles.actions}>
        <button className={styles.button} type="submit" disabled={saving}>
          {saving ? "Saving…" : question ? "Save question" : "Create question"}
        </button>
        <button
          className={styles.secondaryButton}
          type="button"
          disabled={saving}
          onClick={onCancel}
        >
          Cancel
        </button>
      </div>
    </form>
  );
}

function QuestionsContent() {
  const [activeFilter, setActiveFilter] = useState<"" | "true" | "false">("");
  const [pageNumber, setPageNumber] = useState(0);
  const [retry, setRetry] = useState(0);
  const [reload, setReload] = useState(0);
  const key = `${activeFilter}:${pageNumber}:${retry}:${reload}`;
  const [state, setState] = useState<QuestionListState>({
    key: "",
    status: "loading",
  });
  const [editing, setEditing] = useState<FeedbackQuestion | null>(null);
  const [creating, setCreating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void listQuestions({
      active: activeFilter ? activeFilter === "true" : undefined,
      page: pageNumber,
      signal: controller.signal,
    })
      .then((page) => {
        if (active) setState({ key, status: "ready", page });
      })
      .catch((cause: unknown) => {
        if (active && !controller.signal.aborted) {
          setState({
            key,
            status: "error",
            message:
              cause instanceof ApiError
                ? cause.message
                : "Feedback questions could not be loaded.",
          });
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [activeFilter, key, pageNumber]);

  const visibleState =
    state.key === key ? state : { key, status: "loading" as const };

  async function toggleActive(question: FeedbackQuestion) {
    setPendingId(question.id);
    setActionError(null);
    try {
      await setQuestionActive(question.id, !question.active);
      setReload((value) => value + 1);
    } catch (cause) {
      setActionError(
        cause instanceof Error ? cause.message : "Question status could not be updated.",
      );
    } finally {
      setPendingId(null);
    }
  }

  function saved() {
    setEditing(null);
    setCreating(false);
    setReload((value) => value + 1);
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>Feedback configuration</p>
            <h1 className={styles.title}>Feedback questions</h1>
            <p className={styles.description}>
              Configure the active questions shown to customers.
            </p>
          </div>
          {!creating ? (
            <button
              className={styles.button}
              type="button"
              onClick={() => {
                setEditing(null);
                setCreating(true);
              }}
            >
              Add question
            </button>
          ) : null}
        </header>
        {creating ? (
          <section className={styles.card}>
            <h2 className={styles.rowTitle}>New question</h2>
            <QuestionEditor onSaved={saved} onCancel={() => setCreating(false)} />
          </section>
        ) : null}
        {editing ? (
          <section className={styles.card}>
            <h2 className={styles.rowTitle}>Edit question</h2>
            <QuestionEditor
              key={editing.id}
              question={editing}
              onSaved={saved}
              onCancel={() => setEditing(null)}
            />
          </section>
        ) : null}
        <section className={styles.card} aria-label="Feedback question list">
          <label className={styles.field}>
            <span className={styles.label}>Question status</span>
            <select
              className={styles.select}
              value={activeFilter}
              onChange={(event) => {
                setActiveFilter(event.target.value as "" | "true" | "false");
                setPageNumber(0);
              }}
            >
              <option value="">All questions</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          </label>
          {actionError ? <p className={styles.error} role="alert">{actionError}</p> : null}
          {visibleState.status === "loading" ? (
            <p role="status">Loading feedback questions…</p>
          ) : visibleState.status === "error" ? (
            <div>
              <p className={styles.error} role="alert">{visibleState.message}</p>
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => setRetry((value) => value + 1)}
              >
                Retry
              </button>
            </div>
          ) : visibleState.page.content.length === 0 ? (
            <p className={styles.notice} role="status">
              No feedback questions match this view.
            </p>
          ) : (
            <>
              <ul className={styles.list}>
                {visibleState.page.content.map((question) => (
                  <li className={styles.row} key={question.id}>
                    <div className={styles.stack}>
                      <p className={styles.rowTitle}>{question.questionText}</p>
                      <div className={styles.meta}>
                        <span className={styles.badge}>{question.questionType}</span>
                        <span>{question.required ? "Required" : "Optional"}</span>
                        <span>Order {question.displayOrder}</span>
                        <span>{question.active ? "Active" : "Inactive"}</span>
                      </div>
                    </div>
                    <div className={styles.actions}>
                      <button
                        className={styles.secondaryButton}
                        type="button"
                        onClick={() => {
                          setCreating(false);
                          setEditing(question);
                        }}
                      >
                        Edit
                      </button>
                      <button
                        className={styles.secondaryButton}
                        type="button"
                        disabled={pendingId === question.id}
                        onClick={() => void toggleActive(question)}
                      >
                        {pendingId === question.id
                          ? "Updating…"
                          : question.active
                            ? "Deactivate"
                            : "Activate"}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
              {visibleState.page.totalPages > 1 ? (
                <nav className={styles.pagination} aria-label="Question pages">
                  <button
                    className={styles.secondaryButton}
                    type="button"
                    disabled={!visibleState.page.hasPrevious}
                    onClick={() => setPageNumber((value) => Math.max(0, value - 1))}
                  >
                    Previous
                  </button>
                  <span>
                    Page {visibleState.page.pageNumber + 1} of {visibleState.page.totalPages}
                  </span>
                  <button
                    className={styles.secondaryButton}
                    type="button"
                    disabled={!visibleState.page.hasNext}
                    onClick={() => setPageNumber((value) => value + 1)}
                  >
                    Next
                  </button>
                </nav>
              ) : null}
            </>
          )}
        </section>
      </div>
    </main>
  );
}

export function FeedbackQuestionsPage() {
  return (
    <BusinessRouteGate>
      <QuestionsContent />
    </BusinessRouteGate>
  );
}
