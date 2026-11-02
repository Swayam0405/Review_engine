"use client";

import { createHttpApiError, createNetworkApiError, ApiError } from "./api-error";
import { resolveApiUrl } from "./api-config";
import {
  clearAccessToken,
  expireAccessToken,
  getAccessToken,
  setAccessToken,
} from "../auth/access-token-store";
import type { AuthResponse } from "../../types/auth";

export type ApiMethod = "GET" | "POST" | "PUT" | "PATCH";
export type ApiAuthMode = "none" | "optional" | "required";
export type ApiResponseType = "json" | "blob" | "text";

export interface ApiRequestOptions {
  method?: ApiMethod;
  body?: unknown;
  headers?: HeadersInit;
  auth?: ApiAuthMode;
  responseType?: ApiResponseType;
  credentials?: RequestCredentials;
  signal?: AbortSignal;
  timeoutMs?: number;
}

const DEFAULT_TIMEOUT_MS = 15_000;
const REFRESH_PATH = "/api/auth/refresh";
const LOGOUT_PATH = "/api/auth/logout";
const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

interface PreparedRequest {
  method: ApiMethod;
  body?: string;
  headers: Headers;
  responseType: ApiResponseType;
  signal?: AbortSignal;
  timeoutMs: number;
  credentials: RequestCredentials;
}

let refreshInFlight: Promise<string> | null = null;

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T | undefined> {
  const prepared = prepareRequest(options);
  const authMode = options.auth ?? "none";
  const url = resolveApiUrl(path);

  let token: string | null = null;
  if (authMode !== "none") {
    token = getAccessToken();
  }
  if (authMode === "required" && token === null) {
    token = await refreshAccessToken(null);
  }

  try {
    return await sendRequest<T>(url, prepared, token, authMode);
  } catch (error) {
    if (
      authMode !== "required" ||
      !(error instanceof ApiError) ||
      error.status !== 401
    ) {
      throw error;
    }

    // Only an explicit 401 is retried; ambiguous network failures must not replay writes.
    const refreshedToken = await refreshAccessToken(token);
    return sendRequest<T>(url, prepared, refreshedToken, authMode);
  }
}

export async function refreshSession(): Promise<void> {
  await refreshAccessToken(getAccessToken());
}

export async function logoutSession(): Promise<void> {
  try {
    const headers = csrfHeaders();
    const prepared = prepareRequest({
      method: "POST",
      headers,
      responseType: "json",
    });
    await sendRequest<null>(
      resolveApiUrl(LOGOUT_PATH),
      { ...prepared, credentials: "include" },
      null,
      "none",
    );
  } finally {
    clearAccessToken();
  }
}

function prepareRequest(options: ApiRequestOptions): PreparedRequest {
  const method = options.method ?? "GET";
  if (method === "GET" && options.body !== undefined) {
    throw new ApiError("CONFIGURATION_ERROR", "GET requests cannot include a body.");
  }

  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new ApiError(
      "CONFIGURATION_ERROR",
      "The request timeout must be a positive number.",
    );
  }

  const responseType = options.responseType ?? "json";
  const headers = new Headers(options.headers);
  if (!headers.has("Accept")) {
    headers.set(
      "Accept",
      responseType === "blob" ? "image/png, application/json" : "application/json",
    );
  }

  let body: string | undefined;
  if (options.body !== undefined) {
    try {
      body = JSON.stringify(options.body);
    } catch {
      throw new ApiError(
        "CONFIGURATION_ERROR",
        "The request body could not be serialized as JSON.",
      );
    }
    if (body === undefined) {
      throw new ApiError(
        "CONFIGURATION_ERROR",
        "The request body could not be serialized as JSON.",
      );
    }
    if (!headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
  }

  return {
    method,
    body,
    headers,
    responseType,
    signal: options.signal,
    timeoutMs,
    credentials: options.credentials ?? "omit",
  };
}

async function sendRequest<T>(
  url: string,
  request: PreparedRequest,
  token: string | null,
  authMode: ApiAuthMode,
): Promise<T | undefined> {
  const headers = new Headers(request.headers);
  if (authMode === "none") {
    headers.delete("Authorization");
  } else if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  } else {
    headers.delete("Authorization");
  }

  const timeout = createTimeoutController(request.signal, request.timeoutMs);
  try {
    if (request.signal?.aborted) {
      throw new ApiError("REQUEST_ABORTED", "The request was cancelled.");
    }

    const response = await fetch(url, {
      method: request.method,
      headers,
      body: request.body,
      signal: timeout.signal,
      credentials: request.credentials,
    });
    return await parseResponse<T>(response, request.responseType);
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw createNetworkApiError(error, timeout.didTimeOut());
  } finally {
    timeout.dispose();
  }
}

async function parseResponse<T>(
  response: Response,
  responseType: ApiResponseType,
): Promise<T | undefined> {
  if (!response.ok) {
    throw createHttpApiError(response.status, await readJsonIfPossible(response));
  }

  if (responseType === "blob") {
    return (await response.blob()) as T;
  }

  const text = await response.text();
  if (text.length === 0) {
    return undefined;
  }
  if (responseType === "text") {
    return text as T;
  }

  let payload: unknown;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new ApiError(
      "INVALID_RESPONSE",
      "The server returned an invalid response.",
      response.status,
    );
  }

  if (isApiEnvelope(payload)) {
    if (!payload.success) {
      throw createHttpApiError(
        response.status >= 400 ? response.status : 400,
        payload,
      );
    }
    return payload.data as T | undefined;
  }

  return payload as T;
}

async function readJsonIfPossible(response: Response): Promise<unknown> {
  const body = await response.text();
  if (body.length === 0) {
    return undefined;
  }
  try {
    return JSON.parse(body) as unknown;
  } catch {
    return undefined;
  }
}

function isApiEnvelope(value: unknown): value is {
  success: boolean;
  message?: unknown;
  data?: unknown;
} {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value &&
    typeof value.success === "boolean" &&
    "data" in value
  );
}

function createTimeoutController(
  callerSignal: AbortSignal | undefined,
  timeoutMs: number,
): {
  signal: AbortSignal;
  didTimeOut: () => boolean;
  dispose: () => void;
} {
  const controller = new AbortController();
  let timedOut = false;
  const abortFromCaller = () => controller.abort(callerSignal?.reason);

  if (callerSignal?.aborted) {
    abortFromCaller();
  } else {
    callerSignal?.addEventListener("abort", abortFromCaller, { once: true });
  }

  const timeoutId = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  return {
    signal: controller.signal,
    didTimeOut: () => timedOut,
    dispose: () => {
      clearTimeout(timeoutId);
      callerSignal?.removeEventListener("abort", abortFromCaller);
    },
  };
}

function csrfHeaders(): Headers {
  const headers = new Headers();
  const token = readCsrfCookie();
  if (token) {
    headers.set(CSRF_HEADER_NAME, token);
  }
  return headers;
}

function readCsrfCookie(): string | undefined {
  if (typeof document === "undefined") {
    return undefined;
  }

  const cookie = document.cookie
    .split(";")
    .map((item) => item.trim())
    .find((item) => item.startsWith(`${CSRF_COOKIE_NAME}=`));

  if (!cookie) {
    return undefined;
  }

  return cookie.slice(CSRF_COOKIE_NAME.length + 1);
}

function refreshAccessToken(expectedToken: string | null): Promise<string> {
  const currentToken = getAccessToken();
  if (currentToken !== expectedToken) {
    if (currentToken !== null) {
      return Promise.resolve(currentToken);
    }
    return Promise.reject(
      new ApiError("UNAUTHORIZED", "Authentication is required.", 401),
    );
  }
  if (refreshInFlight) {
    return refreshInFlight;
  }

  // Rotation makes concurrent refreshes unsafe: the backend treats reuse as compromise.
  const flight = performRefresh(expectedToken).finally(() => {
    if (refreshInFlight === flight) {
      refreshInFlight = null;
    }
  });
  refreshInFlight = flight;
  return flight;
}

async function performRefresh(expectedToken: string | null): Promise<string> {
  try {
    const prepared = prepareRequest({
      method: "POST",
      headers: csrfHeaders(),
      responseType: "json",
    });
    prepared.credentials = "include";

    const response = await sendRequest<AuthResponse>(
      resolveApiUrl(REFRESH_PATH),
      prepared,
      null,
      "none",
    );
    if (
      !response ||
      typeof response.accessToken !== "string" ||
      response.accessToken.trim().length === 0 ||
      typeof response.tokenType !== "string" ||
      response.tokenType.toLowerCase() !== "bearer"
    ) {
      throw new ApiError(
        "INVALID_RESPONSE",
        "The session refresh response was invalid.",
      );
    }

    const currentToken = getAccessToken();
    if (currentToken !== expectedToken) {
      if (currentToken !== null) {
        return currentToken;
      }
      throw new ApiError("UNAUTHORIZED", "Authentication is required.", 401);
    }

    setAccessToken(response.accessToken);
    return response.accessToken;
  } catch (error) {
    const currentToken = getAccessToken();
    if (currentToken !== expectedToken) {
      if (currentToken !== null) {
        return currentToken;
      }
      throw error;
    }

    expireAccessToken();
    throw error;
  }
}
