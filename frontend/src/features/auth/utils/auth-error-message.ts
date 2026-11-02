import { ApiError } from "../../../lib/api/api-error";

export function getAuthErrorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}
