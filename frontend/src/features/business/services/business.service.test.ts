import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createBusiness,
  getGoogleReviewQr,
  getMyBusiness,
  updateBusiness,
  updateGoogleReviewUrl,
} from "./business.service";
import {
  clearAccessToken,
  setAccessToken,
} from "../../../lib/auth/access-token-store";

const profile = {
  id: "business-id",
  name: "Review Shop",
  category: "Retail",
  address: "1 Main Street",
  timezone: "Asia/Kolkata",
  googleReviewUrl: "https://g.page/r/example/review",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

function envelope(data: unknown, success = true, message = "OK") {
  return { success, message, data, timestamp: "2026-01-01T00:00:00Z" };
}

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function pathname(input: RequestInfo | URL): string {
  return new URL(String(input)).pathname;
}

beforeEach(() => {
  clearAccessToken();
  setAccessToken("test-access-token");
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.test");
});

afterEach(() => {
  clearAccessToken();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

describe("business service", () => {
  it("creates a business with only DTO-supported fields", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope(profile)),
    );
    vi.stubGlobal("fetch", fetchMock);
    await createBusiness({
      name: "Review Shop",
      category: "Retail",
      address: "1 Main Street",
      timezone: "Asia/Kolkata",
      googleReviewUrl: null,
    });

    expect(pathname(fetchMock.mock.calls[0]![0])).toBe("/api/business");
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe("POST");
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      name: "Review Shop",
      category: "Retail",
      address: "1 Main Street",
      timezone: "Asia/Kolkata",
      googleReviewUrl: null,
    });
  });

  it("retrieves and updates the authenticated owner's profile", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope(profile)),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(getMyBusiness()).resolves.toEqual(profile);
    await updateBusiness({
      name: "Review Shop",
      category: "Retail",
      address: "1 Main Street",
      timezone: "Asia/Kolkata",
      googleReviewUrl: profile.googleReviewUrl,
    });

    expect(pathname(fetchMock.mock.calls[0]![0])).toBe("/api/business/me");
    expect(pathname(fetchMock.mock.calls[1]![0])).toBe("/api/business/me");
    expect(fetchMock.mock.calls[1]?.[1]?.method).toBe("PUT");
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("Authorization"))
      .toContain("Bearer ");
  });

  it("patches only the backend Google review URL DTO", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope(profile)),
    );
    vi.stubGlobal("fetch", fetchMock);
    await updateGoogleReviewUrl({
      googleReviewUrl: profile.googleReviewUrl,
    });

    expect(pathname(fetchMock.mock.calls[0]![0])).toBe(
      "/api/business/me/google-review-url",
    );
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe("PATCH");
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      googleReviewUrl: profile.googleReviewUrl,
    });
  });

  it("retrieves the actual backend PNG Blob and rejects non-PNG responses", async () => {
    const png = new Blob([new Uint8Array([137, 80, 78, 71])], {
      type: "image/png",
    });
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(png));
    vi.stubGlobal("fetch", fetchMock);
    await expect(getGoogleReviewQr()).resolves.toMatchObject({
      type: "image/png",
    });
    expect(pathname(fetchMock.mock.calls[0]![0])).toBe(
      "/api/business/me/google-review-qr",
    );

    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () => new Response("not png")),
    );
    await expect(getGoogleReviewQr()).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });

  it("preserves backend missing-profile and validation errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () =>
        jsonResponse(envelope(null, false, "Business onboarding is required."), 404),
      ),
    );
    await expect(getMyBusiness()).rejects.toMatchObject({
      status: 404,
      message: "Business onboarding is required.",
    });

    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () =>
        jsonResponse(
          envelope(null, false, "A valid IANA timezone is required."),
          400,
        ),
      ),
    );
    await expect(
      updateBusiness({
        name: "Review Shop",
        category: "Retail",
        address: "1 Main Street",
        timezone: "Bad/Zone",
        googleReviewUrl: null,
      }),
    ).rejects.toMatchObject({
      status: 400,
      message: "A valid IANA timezone is required.",
    });
  });
});
