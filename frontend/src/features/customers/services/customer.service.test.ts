import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAccessToken,
  setAccessToken,
} from "../../../lib/auth/access-token-store";
import {
  createCustomer,
  getCustomer,
  listCustomers,
  updateCustomer,
} from "./customer.service";

const customer = {
  id: "customer-uuid",
  name: "A Customer",
  phone: "+14155552671",
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

function pathOf(input: RequestInfo | URL): URL {
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

describe("customer service", () => {
  it("lists and searches with supported pagination and name query parameters", async () => {
    const response = {
      content: [
        {
          id: customer.id,
          name: customer.name,
          createdAt: customer.createdAt,
          updatedAt: customer.updatedAt,
        },
      ],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    };
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope(response)),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(listCustomers({ name: "  Alice & Bob ", page: 2 })).resolves.toEqual(
      response,
    );
    const requestUrl = pathOf(fetchMock.mock.calls[0]![0]);
    expect(requestUrl.pathname).toBe("/api/customers");
    expect(requestUrl.searchParams.get("page")).toBe("2");
    expect(requestUrl.searchParams.get("size")).toBe("20");
    expect(requestUrl.searchParams.get("sort")).toBe("createdAt,desc");
    expect(requestUrl.searchParams.get("name")).toBe("Alice & Bob");
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("Authorization"))
      .toBe("Bearer test-access-token");
  });

  it("gets, creates and updates only the supported customer fields", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse(envelope(customer)),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(getCustomer(customer.id)).resolves.toEqual(customer);
    await expect(createCustomer({ name: customer.name, phone: customer.phone }))
      .resolves.toEqual(customer);
    await expect(
      updateCustomer(customer.id, { name: "Updated", phone: customer.phone }),
    ).resolves.toEqual(customer);

    expect(pathOf(fetchMock.mock.calls[0]![0]).pathname)
      .toBe(`/api/customers/${customer.id}`);
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe("GET");
    expect(pathOf(fetchMock.mock.calls[1]![0]).pathname).toBe("/api/customers");
    expect(fetchMock.mock.calls[1]?.[1]?.method).toBe("POST");
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      name: customer.name,
      phone: customer.phone,
    });
    expect(fetchMock.mock.calls[2]?.[1]?.method).toBe("PUT");
    expect(JSON.parse(String(fetchMock.mock.calls[2]?.[1]?.body))).toEqual({
      name: "Updated",
      phone: customer.phone,
    });
    for (const [, init] of fetchMock.mock.calls) {
      expect(new Headers(init?.headers).get("Authorization")).toBe(
        "Bearer test-access-token",
      );
    }
  });

  it("preserves safe backend errors and rejects malformed response data", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () =>
        jsonResponse(envelope(null, false, "A customer with this phone number already exists."), 409),
      ),
    );
    await expect(createCustomer({ name: "Name", phone: "+14155552671" }))
      .rejects.toMatchObject({
        status: 409,
        message: "A customer with this phone number already exists.",
      });

    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () => jsonResponse(envelope({ content: [] }))),
    );
    await expect(listCustomers()).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });
});
