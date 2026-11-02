import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAccessToken,
  setAccessToken,
} from "../../../lib/auth/access-token-store";
import {
  createTransaction,
  getTransaction,
  listTransactions,
} from "./transaction.service";

const transaction = {
  id: "transaction-uuid",
  customerId: "customer-uuid",
  customerName: "Alex Customer",
  referenceCode: "ORDER-12",
  status: "COMPLETED",
  completedAt: "2026-10-31T12:00:00Z",
  createdAt: "2026-10-31T12:00:01Z",
  updatedAt: "2026-10-31T12:00:01Z",
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

function requestUrl(input: RequestInfo | URL): URL {
  return new URL(String(input));
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

describe("transaction service", () => {
  it("lists paginated transactions with the supported reference filter", async () => {
    const page = {
      content: [transaction],
      pageNumber: 1,
      pageSize: 20,
      totalElements: 21,
      totalPages: 2,
      hasNext: false,
      hasPrevious: true,
    };
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope(page)),
    );
    vi.stubGlobal("fetch", fetchMock);
    await expect(
      listTransactions({ page: 1, referenceCode: "  ORD- " }),
    ).resolves.toEqual(page);

    const url = requestUrl(fetchMock.mock.calls[0]![0]);
    expect(url.pathname).toBe("/api/transactions");
    expect(url.searchParams.get("page")).toBe("1");
    expect(url.searchParams.get("size")).toBe("20");
    expect(url.searchParams.get("sort")).toBe("completedAt,desc");
    expect(url.searchParams.get("referenceCode")).toBe("ORD-");
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("Authorization"))
      .toBe("Bearer test-access-token");
  });

  it("gets details and creates with only DTO-supported fields", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope(transaction)),
    );
    vi.stubGlobal("fetch", fetchMock);
    await expect(getTransaction(transaction.id)).resolves.toEqual(transaction);
    await expect(
      createTransaction({
        customerId: transaction.customerId,
        referenceCode: "ORDER-12",
        completedAt: transaction.completedAt,
      }),
    ).resolves.toEqual(transaction);

    expect(requestUrl(fetchMock.mock.calls[0]![0]).pathname)
      .toBe(`/api/transactions/${transaction.id}`);
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe("GET");
    expect(requestUrl(fetchMock.mock.calls[1]![0]).pathname)
      .toBe("/api/transactions");
    expect(fetchMock.mock.calls[1]?.[1]?.method).toBe("POST");
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      customerId: transaction.customerId,
      referenceCode: "ORDER-12",
      completedAt: transaction.completedAt,
    });
    expect(
      JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body)).status,
    ).toBeUndefined();
  });

  it("normalizes backend errors and rejects unsupported status response values", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () =>
        jsonResponse(envelope(null, false, "A transaction with this reference code already exists."), 409),
      ),
    );
    await expect(
      createTransaction({ customerId: transaction.customerId }),
    ).rejects.toMatchObject({
      status: 409,
      message: "A transaction with this reference code already exists.",
    });

    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () =>
        jsonResponse(envelope({ ...transaction, status: "PENDING" })),
      ),
    );
    await expect(getTransaction(transaction.id)).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });
});
