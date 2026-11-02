export type ApiErrorCode =
  | "BAD_REQUEST"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "UNPROCESSABLE_ENTITY"
  | "RATE_LIMITED"
  | "SERVER_ERROR"
  | "HTTP_ERROR"
  | "NETWORK_ERROR"
  | "TIMEOUT"
  | "REQUEST_ABORTED"
  | "INVALID_RESPONSE"
  | "CONFIGURATION_ERROR";

export class ApiError extends Error {
  readonly code: ApiErrorCode;
  readonly status?: number;

  constructor(code: ApiErrorCode, message: string, status?: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

const statusErrors: Record<number, { code: ApiErrorCode; message: string }> = {
  400: { code: "BAD_REQUEST", message: "The request could not be completed." },
  401: { code: "UNAUTHORIZED", message: "Authentication is required." },
  403: { code: "FORBIDDEN", message: "You do not have access to this resource." },
  404: { code: "NOT_FOUND", message: "The requested resource was not found." },
  409: { code: "CONFLICT", message: "The request conflicts with existing data." },
  422: {
    code: "UNPROCESSABLE_ENTITY",
    message: "The submitted data could not be processed.",
  },
  429: {
    code: "RATE_LIMITED",
    message: "Too many requests. Please wait and try again.",
  },
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function backendMessage(payload: unknown): string | undefined {
  if (!isRecord(payload) || payload.success !== false) {
    return undefined;
  }

  const { message } = payload;
  if (
    typeof message !== "string" ||
    message.length > 300 ||
    /[\r\n\0]/.test(message)
  ) {
    return undefined;
  }

  const normalized = message.trim();
  return normalized.length > 0 ? normalized : undefined;
}

export function createHttpApiError(status: number, payload?: unknown): ApiError {
  const defaults = statusErrors[status];
  const code =
    defaults?.code ?? (status >= 500 ? "SERVER_ERROR" : "HTTP_ERROR");
  const message =
    status >= 500
      ? "The server encountered an error. Please try again later."
      : (backendMessage(payload) ??
        defaults?.message ??
        "The request could not be completed.");

  return new ApiError(code, message, status);
}

export function createNetworkApiError(error: unknown, timedOut: boolean): ApiError {
  if (timedOut) {
    return new ApiError("TIMEOUT", "The request timed out. Please try again.");
  }

  if (
    typeof error === "object" &&
    error !== null &&
    "name" in error &&
    error.name === "AbortError"
  ) {
    return new ApiError("REQUEST_ABORTED", "The request was cancelled.");
  }

  return new ApiError(
    "NETWORK_ERROR",
    "Unable to reach the server. Check your connection and try again.",
  );
}
