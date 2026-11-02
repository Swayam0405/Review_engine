import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type {
  Customer,
  CustomerInput,
  CustomerPage,
  CustomerSummary,
} from "../types/customer.types";

const CUSTOMERS_PATH = "/api/customers";
const PAGE_SIZE = 20;

export async function listCustomers(
  options: { name?: string; page?: number; signal?: AbortSignal } = {},
): Promise<CustomerPage> {
  const query = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(PAGE_SIZE),
    sort: "createdAt,desc",
  });
  if (options.name?.trim()) {
    query.set("name", options.name.trim());
  }
  const value = await apiRequest<unknown>(`${CUSTOMERS_PATH}?${query}`, {
    auth: "required",
    signal: options.signal,
  });
  return parseCustomerPage(value);
}

export async function getCustomer(
  customerId: string,
  signal?: AbortSignal,
): Promise<Customer> {
  const value = await apiRequest<unknown>(
    `${CUSTOMERS_PATH}/${encodeURIComponent(customerId)}`,
    { auth: "required", signal },
  );
  return parseCustomer(value);
}

export async function createCustomer(input: CustomerInput): Promise<Customer> {
  const value = await apiRequest<unknown>(CUSTOMERS_PATH, {
    method: "POST",
    body: input,
    auth: "required",
  });
  return parseCustomer(value);
}

export async function updateCustomer(
  customerId: string,
  input: CustomerInput,
): Promise<Customer> {
  const value = await apiRequest<unknown>(
    `${CUSTOMERS_PATH}/${encodeURIComponent(customerId)}`,
    { method: "PUT", body: input, auth: "required" },
  );
  return parseCustomer(value);
}

function parseCustomerPage(value: unknown): CustomerPage {
  if (
    !isRecord(value) ||
    !Array.isArray(value.content) ||
    !isNumber(value.pageNumber) ||
    !isNumber(value.pageSize) ||
    !isNumber(value.totalElements) ||
    !isNumber(value.totalPages) ||
    typeof value.hasNext !== "boolean" ||
    typeof value.hasPrevious !== "boolean"
  ) {
    throw invalidResponse();
  }
  return {
    content: value.content.map(parseCustomerSummary),
    pageNumber: value.pageNumber,
    pageSize: value.pageSize,
    totalElements: value.totalElements,
    totalPages: value.totalPages,
    hasNext: value.hasNext,
    hasPrevious: value.hasPrevious,
  };
}

function parseCustomerSummary(value: unknown): CustomerSummary {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.name !== "string" ||
    typeof value.createdAt !== "string" ||
    typeof value.updatedAt !== "string"
  ) {
    throw invalidResponse();
  }
  return {
    id: value.id,
    name: value.name,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  };
}

function parseCustomer(value: unknown): Customer {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.name !== "string" ||
    typeof value.phone !== "string" ||
    typeof value.createdAt !== "string" ||
    typeof value.updatedAt !== "string"
  ) {
    throw invalidResponse();
  }
  return {
    id: value.id,
    name: value.name,
    phone: value.phone,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function invalidResponse(): ApiError {
  return new ApiError(
    "INVALID_RESPONSE",
    "The server returned invalid customer data.",
  );
}
