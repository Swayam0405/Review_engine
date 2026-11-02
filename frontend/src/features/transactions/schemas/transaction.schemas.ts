import type {
  CreateTransactionInput,
  TransactionFieldErrors,
} from "../types/transaction.types";

const CONTROL_CHARACTERS = /\p{Cc}/u;

export function validateTransaction(
  input: CreateTransactionInput,
): TransactionFieldErrors {
  const errors: TransactionFieldErrors = {};
  if (!input.customerId.trim()) {
    errors.customerId = "Select a customer.";
  }

  const referenceCode = input.referenceCode?.trim();
  if (
    referenceCode &&
    (referenceCode.length > 100 || CONTROL_CHARACTERS.test(referenceCode))
  ) {
    errors.referenceCode =
      "Reference code must be 100 characters or fewer without control characters.";
  }
  if (input.completedAt) {
    const completedAt = new Date(input.completedAt);
    if (Number.isNaN(completedAt.getTime())) {
      errors.completedAt = "Enter a valid completion date and time.";
    } else if (completedAt.getTime() > Date.now() + 5 * 60 * 1000) {
      errors.completedAt =
        "Completion time cannot be more than five minutes in the future.";
    }
  }
  return errors;
}
