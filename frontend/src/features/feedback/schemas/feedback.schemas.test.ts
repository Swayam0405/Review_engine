import { describe, expect, it } from "vitest";
import {
  buildFeedbackAnswers,
  validateFeedbackQuestion,
  validateFeedbackSubmission,
} from "./feedback.schemas";

describe("feedback validation", () => {
  it("validates question text and non-negative integer order", () => {
    expect(
      validateFeedbackQuestion({
        questionText: "  ",
        questionType: "TEXT",
        required: true,
        displayOrder: -1,
        active: true,
      }),
    ).toEqual({
      questionText: "Question text is required.",
      displayOrder: "Display order must be a non-negative whole number.",
    });
  });

  it("builds only typed answers and requires active required questions", () => {
    const result = buildFeedbackAnswers(
      [
        {
          id: "text",
          questionText: "Describe",
          questionType: "TEXT",
          required: true,
          active: true,
          displayOrder: 0,
          createdAt: "",
          updatedAt: "",
        },
        {
          id: "rating",
          questionText: "Rate",
          questionType: "RATING",
          required: true,
          active: true,
          displayOrder: 1,
          createdAt: "",
          updatedAt: "",
        },
        {
          id: "yes-no",
          questionText: "Recommend?",
          questionType: "YES_NO",
          required: false,
          active: true,
          displayOrder: 2,
          createdAt: "",
          updatedAt: "",
        },
      ],
      { text: "Good service", rating: "5", "yes-no": "false" },
    );
    expect(result).toEqual({
      answers: [
        { questionId: "text", answerText: "Good service" },
        { questionId: "rating", answerRating: 5 },
        { questionId: "yes-no", answerBoolean: false },
      ],
      errors: {},
    });

    expect(buildFeedbackAnswers([], {}).errors).toEqual({});
    expect(
      buildFeedbackAnswers(
        [
          {
            id: "required",
            questionText: "Required",
            questionType: "YES_NO",
            required: true,
            active: true,
            displayOrder: 0,
            createdAt: "",
            updatedAt: "",
          },
        ],
        {},
      ).errors,
    ).toEqual({ required: "This question is required." });
  });

  it("enforces overall rating and comment constraints", () => {
    expect(validateFeedbackSubmission({ rating: null, comment: "" })).toHaveProperty(
      "rating",
    );
    expect(validateFeedbackSubmission({ rating: 5, comment: "Fine\u0001" })).toHaveProperty(
      "comment",
    );
  });
});
