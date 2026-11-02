import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type {
  CreateTransactionInput,
  Transaction,
  TransactionPage,
} from "../types/transaction.types";

const TRANSACTIONS_PATH = "/api/transactions";
const PAGE_SIZE = 20;

export async function listTransactions(
  options: {
    page?: number;
    referenceCode?: string;
    signal?: AbortSignal;
  } = {},
): Promise<TransactionPage> {
  const query = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(PAGE_SIZE),
    sort: "completedAt,desc",
  });
  if (options.referenceCode?.trim()) {
    query.set("referenceCode", options.referenceCode.trim());
  }
  const result = await apiRequest<unknown>(`${TRANSACTIONS_PATH}?${query}`, {
    auth: "required",
    signal: options.signal,
  });
  return parseTransactionPage(result);
}

export async function getTransaction(
  transactionId: string,
  signal?: AbortSignal,
): Promise<Transaction> {
  const result = await apiRequest<unknown>(
    `${TRANSACTIONS_PATH}/${encodeURIComponent(transactionId)}`,
    { auth: "required", signal },
  );
  return parseTransaction(result);
}

export async function createTransaction(
  input: CreateTransactionInput,
): Promise<Transaction> {
  const result = await apiRequest<unknown>(TRANSACTIONS_PATH, {
    method: "POST",
    body: input,
    auth: "required",
  });
  return parseTransaction(result);
}

function parseTransactionPage(value: unknown): TransactionPage {
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
    content: value.content.map(parseTransaction),
    pageNumber: value.pageNumber,
    pageSize: value.pageSize,
    totalElements: value.totalElements,
    totalPages: value.totalPages,
    hasNext: value.hasNext,
    hasPrevious: value.hasPrevious,
  };
}

function parseTransaction(value: unknown): Transaction {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.customerId !== "string" ||
    typeof value.customerName !== "string" ||
    !(value.referenceCode === null || typeof value.referenceCode === "string") ||
    value.status !== "COMPLETED" ||
    typeof value.completedAt !== "string" ||
    typeof value.createdAt !== "string" ||
    typeof value.updatedAt !== "string"
  ) {
    throw invalidResponse();
  }
  return {
    id: value.id,
    customerId: value.customerId,
    customerName: value.customerName,
    referenceCode: value.referenceCode,
    status: value.status,
    completedAt: value.completedAt,
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
    "The server returned invalid transaction data.",
  );
}
