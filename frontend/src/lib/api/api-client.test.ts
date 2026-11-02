import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api-error";
import { apiRequest, logoutSession } from "./api-client";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeToAuthState,
} from "../auth/access-token-store";

const API_BASE_URL = "https://api.example.test";

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function successEnvelope<T>(data: T): {
  success: true;
  message: string;
  data: T;
  timestamp: string;
} {
  return {
    success: true,
    message: "OK",
    data,
    timestamp: "2026-01-01T00:00:00Z",
  };
}

function authResponse(accessToken: string): Response {
  return jsonResponse(
    successEnvelope({
      accessToken,
      tokenType: "Bearer",
      user: {
        id: "owner-id",
        name: "Owner",
        email: "owner@example.test",
        accountStatus: "ACTIVE",
        emailVerified: true,
        hasBusiness: false,
      },
    }),
  );
}

function installBrowserCookies(cookie: string): void {
  vi.stubGlobal("document", { cookie });
}

function installFetch(
  implementation: typeof fetch,
): ReturnType<typeof vi.fn<typeof fetch>> {
  const fetchMock = vi.fn<typeof fetch>(implementation);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function responsePath(input: RequestInfo | URL): string {
  return new URL(String(input)).pathname;
}

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", API_BASE_URL);
  clearAccessToken();
});

afterEach(() => {
  clearAccessToken();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.useRealTimers();
});

describe("API URL and request construction", () => {
  it("uses the configured API base URL without hardcoded host assumptions", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.test/v1/");
    const fetchMock = installFetch(async () => jsonResponse(successEnvelope("ok")));

    await apiRequest<string>("/api/status");

    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "https://api.example.test/v1/api/status",
    );
  });

  it("fails explicitly when the API base URL is missing", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");
    const fetchMock = installFetch(async () => jsonResponse(successEnvelope("ok")));

    await expect(apiRequest("/api/status")).rejects.toMatchObject({
      code: "CONFIGURATION_ERROR",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(["POST", "PUT", "PATCH"] as const)(
    "sends %s JSON requests with the correct method and content type",
    async (method) => {
      const fetchMock = installFetch(async () => jsonResponse(successEnvelope("ok")));
      const body = { displayName: "Example" };

      await apiRequest("/api/resource", { method, body });

      const init = fetchMock.mock.calls[0]?.[1];
      expect(init?.method).toBe(method);
      expect(init?.body).toBe(JSON.stringify(body));
      expect(new Headers(init?.headers).get("Content-Type")).toBe(
        "application/json",
      );
    },
  );

  it("omits cookies by default and includes them only when explicitly requested", async () => {
    const fetchMock = installFetch(async () => jsonResponse(successEnvelope("ok")));

    await apiRequest("/api/public");
    await apiRequest("/api/auth/login", {
      method: "POST",
      body: { email: "owner@example.com", password: "secret" },
      credentials: "include",
    });

    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe("omit");
    expect(fetchMock.mock.calls[1]?.[1]?.credentials).toBe("include");
  });

  it("returns undefined for a successful empty response", async () => {
    installFetch(async () => new Response(null, { status: 204 }));

    await expect(apiRequest("/api/empty")).resolves.toBeUndefined();
  });

  it("returns an unwrapped JSON envelope and accepts a non-envelope JSON body", async () => {
    const fetchMock = installFetch(async (input) =>
      responsePath(input) === "/api/envelope"
        ? jsonResponse(successEnvelope({ value: 1 }))
        : jsonResponse({ value: 2 }),
    );

    await expect(apiRequest<{ value: number }>("/api/envelope")).resolves.toEqual(
      { value: 1 },
    );
    await expect(apiRequest<{ value: number }>("/api/plain-json")).resolves.toEqual(
      { value: 2 },
    );
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("returns text and Blob response bodies when requested", async () => {
    const fetchMock = installFetch(async (_input, init) =>
      init?.headers &&
      new Headers(init.headers).get("Accept")?.includes("image/png")
        ? new Response(new Uint8Array([1, 2, 3]), {
            headers: { "Content-Type": "image/png" },
          })
        : new Response("plain response"),
    );

    await expect(
      apiRequest<string>("/api/text", { responseType: "text" }),
    ).resolves.toBe("plain response");
    const image = await apiRequest<Blob>("/api/image", {
      responseType: "blob",
    });
    expect(image).toBeInstanceOf(Blob);
    expect(image?.type).toBe("image/png");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("does not allow GET request bodies", async () => {
    const fetchMock = installFetch(async () => jsonResponse(successEnvelope(null)));

    await expect(apiRequest("/api/resource", { body: {} })).rejects.toMatchObject({
      code: "CONFIGURATION_ERROR",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("API errors and request lifecycle", () => {
  it("preserves safe backend validation messages", async () => {
    const fetchMock = installFetch(async () =>
      jsonResponse(
        {
          success: false,
          message: "Invalid value for field: email.",
          data: null,
          timestamp: "2026-01-01T00:00:00Z",
        },
        400,
      ),
    );

    await expect(apiRequest("/api/resource")).rejects.toMatchObject({
      name: "ApiError",
      code: "BAD_REQUEST",
      status: 400,
      message: "Invalid value for field: email.",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it.each([
    [403, "FORBIDDEN"],
    [404, "NOT_FOUND"],
    [409, "CONFLICT"],
    [422, "UNPROCESSABLE_ENTITY"],
    [429, "RATE_LIMITED"],
    [500, "SERVER_ERROR"],
  ] as const)("normalizes HTTP %i errors", async (status, code) => {
    installFetch(async () => new Response("<html>internal error</html>", { status }));

    await expect(apiRequest("/api/resource")).rejects.toMatchObject({
      code,
      status,
    });
  });

  it("does not expose server error details from a JSON error envelope", async () => {
    installFetch(async () =>
      jsonResponse(
        {
          success: false,
          message: "java.lang.IllegalStateException: internal stack",
          data: null,
          timestamp: "2026-01-01T00:00:00Z",
        },
        500,
      ),
    );

    await expect(apiRequest("/api/resource")).rejects.toMatchObject({
      code: "SERVER_ERROR",
      message: "The server encountered an error. Please try again later.",
    });
  });

  it("normalizes network errors without retrying an unsafe request", async () => {
    const fetchMock = installFetch(async () => {
      throw new TypeError("private low-level detail");
    });

    await expect(
      apiRequest("/api/resource", {
        method: "POST",
        body: { value: true },
        auth: "required",
      }),
    ).rejects.toMatchObject({
      code: "NETWORK_ERROR",
      message: "Unable to reach the server. Check your connection and try again.",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("reports caller cancellation", async () => {
    const fetchMock = installFetch(async () => jsonResponse(successEnvelope(null)));
    const controller = new AbortController();
    controller.abort();

    await expect(
      apiRequest("/api/resource", { signal: controller.signal }),
    ).rejects.toMatchObject({ code: "REQUEST_ABORTED" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("cancels an in-flight request when its caller aborts", async () => {
    const fetchMock = installFetch(
      (_input, init) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => {
            reject(new DOMException("Aborted", "AbortError"));
          });
        }),
    );
    const controller = new AbortController();
    const request = apiRequest("/api/slow", { signal: controller.signal });
    const requestAssertion = expect(request).rejects.toMatchObject({
      code: "REQUEST_ABORTED",
    });

    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    controller.abort();

    await requestAssertion;
  });

  it("aborts requests when the configured timeout expires", async () => {
    vi.useFakeTimers();
    const fetchMock = installFetch(
      (_input, init) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => {
            reject(new DOMException("Aborted", "AbortError"));
          });
        }),
    );

    const request = apiRequest("/api/slow", { timeoutMs: 25 });
    const requestAssertion = expect(request).rejects.toMatchObject({
      code: "TIMEOUT",
    });
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    await vi.advanceTimersByTimeAsync(25);

    await requestAssertion;
  });
});

describe("authentication-aware requests", () => {
  it("attaches bearer tokens only when the request opts into authentication", async () => {
    setAccessToken("memory-access-token");
    const fetchMock = installFetch(async () => jsonResponse(successEnvelope("ok")));

    await apiRequest("/api/public");
    await apiRequest("/api/optional", { auth: "optional" });
    await apiRequest("/api/protected", { auth: "required" });

    expect(
      new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("Authorization"),
    ).toBeNull();
    expect(
      new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get("Authorization"),
    ).toBe("Bearer memory-access-token");
    expect(
      new Headers(fetchMock.mock.calls[2]?.[1]?.headers).get("Authorization"),
    ).toBe("Bearer memory-access-token");
  });

  it("restores a missing in-memory token with a credentialed CSRF-protected refresh", async () => {
    installBrowserCookies("XSRF-TOKEN=csrf-value");
    const fetchMock = installFetch(async (input) =>
      responsePath(input) === "/api/auth/refresh"
        ? authResponse("restored-token")
        : jsonResponse(successEnvelope("protected data")),
    );

    await expect(
      apiRequest<string>("/api/protected", { auth: "required" }),
    ).resolves.toBe("protected data");
    expect(getAccessToken()).toBe("restored-token");
    expect(responsePath(fetchMock.mock.calls[0]![0])).toBe("/api/auth/refresh");
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe("include");
    expect(
      new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("X-XSRF-TOKEN"),
    ).toBe("csrf-value");
  });

  it("refreshes once and retries an unauthorized request with the new token", async () => {
    setAccessToken("expired-token");
    installBrowserCookies("other=value; XSRF-TOKEN=csrf-value");
    let protectedCalls = 0;
    let refreshCalls = 0;
    const fetchMock = installFetch(async (input, init) => {
      if (responsePath(input) === "/api/auth/refresh") {
        refreshCalls += 1;
        return authResponse("rotated-token");
      }

      protectedCalls += 1;
      const authorization = new Headers(init?.headers).get("Authorization");
      return authorization === "Bearer rotated-token"
        ? jsonResponse(successEnvelope("retried"))
        : jsonResponse(
            { success: false, message: "Authentication is required.", data: null },
            401,
          );
    });

    await expect(
      apiRequest<string>("/api/protected", { auth: "required" }),
    ).resolves.toBe("retried");
    expect(protectedCalls).toBe(2);
    expect(refreshCalls).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("shares one refresh operation across concurrent unauthorized requests", async () => {
    setAccessToken("expired-token");
    installBrowserCookies("XSRF-TOKEN=csrf-value");
    let releaseRefresh: ((response: Response) => void) | undefined;
    let refreshCalls = 0;
    const fetchMock = installFetch(async (input, init) => {
      if (responsePath(input) === "/api/auth/refresh") {
        refreshCalls += 1;
        return new Promise<Response>((resolve) => {
          releaseRefresh = resolve;
        });
      }

      const authorization = new Headers(init?.headers).get("Authorization");
      return authorization === "Bearer rotated-token"
        ? jsonResponse(successEnvelope(responsePath(input)))
        : jsonResponse(
            { success: false, message: "Authentication is required.", data: null },
            401,
          );
    });

    const requests = [
      apiRequest<string>("/api/one", { auth: "required" }),
      apiRequest<string>("/api/two", { auth: "required" }),
      apiRequest<string>("/api/three", { auth: "required" }),
    ];

    await vi.waitFor(() => expect(refreshCalls).toBe(1));
    releaseRefresh?.(authResponse("rotated-token"));

    await expect(Promise.all(requests)).resolves.toEqual([
      "/api/one",
      "/api/two",
      "/api/three",
    ]);
    expect(refreshCalls).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(7);
  });

  it("clears the token and notifies the auth layer when refresh fails", async () => {
    setAccessToken("expired-token");
    installBrowserCookies("XSRF-TOKEN=csrf-value");
    const changes: string[] = [];
    const unsubscribe = subscribeToAuthState((change) => {
      changes.push(change.type);
    });
    installFetch(async (input) =>
      responsePath(input) === "/api/auth/refresh"
        ? jsonResponse(
            { success: false, message: "Refresh token has expired.", data: null },
            401,
          )
        : jsonResponse(
            { success: false, message: "Authentication is required.", data: null },
            401,
          ),
    );

    try {
      await expect(
        apiRequest("/api/protected", { auth: "required" }),
      ).rejects.toBeInstanceOf(ApiError);
      expect(getAccessToken()).toBeNull();
      expect(changes).toContain("session-expired");
    } finally {
      unsubscribe();
    }
  });

  it("does not refresh recursively after the retried request is unauthorized", async () => {
    setAccessToken("expired-token");
    installBrowserCookies("XSRF-TOKEN=csrf-value");
    let protectedCalls = 0;
    let refreshCalls = 0;
    const fetchMock = installFetch(async (input) => {
      if (responsePath(input) === "/api/auth/refresh") {
        refreshCalls += 1;
        return authResponse("rotated-token");
      }
      protectedCalls += 1;
      return jsonResponse(
        { success: false, message: "Authentication is required.", data: null },
        401,
      );
    });

    await expect(
      apiRequest("/api/protected", { auth: "required" }),
    ).rejects.toMatchObject({ code: "UNAUTHORIZED", status: 401 });
    expect(protectedCalls).toBe(2);
    expect(refreshCalls).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("does not refresh public requests after an unauthorized response", async () => {
    const fetchMock = installFetch(async () =>
      jsonResponse(
        { success: false, message: "Authentication is required.", data: null },
        401,
      ),
    );

    await expect(apiRequest("/api/public")).rejects.toMatchObject({
      code: "UNAUTHORIZED",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("sends logout with credentials and CSRF header, then clears in-memory auth", async () => {
    setAccessToken("memory-access-token");
    installBrowserCookies("refresh_token=not-readable; XSRF-TOKEN=csrf-value");
    const fetchMock = installFetch(async () => jsonResponse(successEnvelope(null)));

    await logoutSession();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(responsePath(fetchMock.mock.calls[0]![0])).toBe("/api/auth/logout");
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe("include");
    expect(
      new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("X-XSRF-TOKEN"),
    ).toBe("csrf-value");
    expect(
      new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("Authorization"),
    ).toBeNull();
    expect(getAccessToken()).toBeNull();
  });

  it("does not restore a rotated token after logout wins an in-flight refresh race", async () => {
    setAccessToken("expired-token");
    installBrowserCookies("XSRF-TOKEN=csrf-value");
    let releaseRefresh: ((response: Response) => void) | undefined;
    const fetchMock = installFetch(async (input) => {
      if (responsePath(input) === "/api/auth/refresh") {
        return new Promise<Response>((resolve) => {
          releaseRefresh = resolve;
        });
      }
      if (responsePath(input) === "/api/auth/logout") {
        return jsonResponse(successEnvelope(null));
      }
      return jsonResponse(
        { success: false, message: "Authentication is required.", data: null },
        401,
      );
    });

    const protectedRequest = apiRequest("/api/protected", { auth: "required" });
    const protectedAssertion = expect(protectedRequest).rejects.toMatchObject({
      code: "UNAUTHORIZED",
    });
    await vi.waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          ([input]) => responsePath(input) === "/api/auth/refresh",
        ),
      ).toBe(true),
    );

    await logoutSession();
    expect(getAccessToken()).toBeNull();
    releaseRefresh?.(authResponse("rotated-token"));

    await protectedAssertion;
    expect(getAccessToken()).toBeNull();
  });
});
