import type {
  FeedbackAnswerInput,
  FeedbackQuestionInput,
  FeedbackQuestionType,
  PublicFeedbackQuestion,
} from "../types/feedback.types";

const CONTROL_CHARACTERS = /\p{Cc}/u;

export function validateFeedbackQuestion(
  input: FeedbackQuestionInput,
): Record<string, string> {
  const errors: Record<string, string> = {};
  const questionText = input.questionText.trim();
  if (!questionText) {
    errors.questionText = "Question text is required.";
  } else if (questionText.length > 1000 || CONTROL_CHARACTERS.test(questionText)) {
    errors.questionText =
      "Question text must be 1000 characters or fewer without control characters.";
  }
  if (!Number.isInteger(input.displayOrder) || input.displayOrder < 0) {
    errors.displayOrder = "Display order must be a non-negative whole number.";
  }
  return errors;
}

export function buildFeedbackAnswers(
  questions: PublicFeedbackQuestion[],
  values: Record<string, string>,
): { answers: FeedbackAnswerInput[]; errors: Record<string, string> } {
  const answers: FeedbackAnswerInput[] = [];
  const errors: Record<string, string> = {};
  for (const question of questions) {
    const value = values[question.id] ?? "";
    if (!value.trim()) {
      if (question.required) {
        errors[question.id] = "This question is required.";
      }
      continue;
    }

    switch (question.questionType) {
      case "TEXT":
        if (value.trim().length > 5000 || CONTROL_CHARACTERS.test(value.trim())) {
          errors[question.id] =
            "Answer must be 5000 characters or fewer without control characters.";
        } else {
          answers.push({ questionId: question.id, answerText: value.trim() });
        }
        break;
      case "RATING": {
        const rating = Number(value);
        if (!Number.isInteger(rating) || rating < 1 || rating > 5) {
          errors[question.id] = "Choose a rating from 1 to 5.";
        } else {
          answers.push({ questionId: question.id, answerRating: rating });
        }
        break;
      }
      case "YES_NO":
        if (value !== "true" && value !== "false") {
          errors[question.id] = "Choose yes or no.";
        } else {
          answers.push({ questionId: question.id, answerBoolean: value === "true" });
        }
        break;
    }
  }
  return { answers, errors };
}

export function isFeedbackQuestionType(
  value: string,
): value is FeedbackQuestionType {
  return value === "TEXT" || value === "RATING" || value === "YES_NO";
}

export function validateFeedbackSubmission(input: {
  rating: number | null;
  comment: string;
}): Record<string, string> {
  const errors: Record<string, string> = {};
  if (input.rating === null || input.rating < 1 || input.rating > 5) {
    errors.rating = "Select an overall rating from 1 to 5.";
  }
  const comment = input.comment.trim();
  if (
    comment &&
    (comment.length > 5000 || CONTROL_CHARACTERS.test(comment))
  ) {
    errors.comment =
      "Comment must be 5000 characters or fewer without control characters.";
  }
  return errors;
}
