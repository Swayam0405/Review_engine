import type { PageResponse } from "../../../types/api";

export interface Transaction {
  id: string;
  customerId: string;
  customerName: string;
  referenceCode: string | null;
  status: "COMPLETED";
  completedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTransactionInput {
  customerId: string;
  referenceCode?: string;
  completedAt?: string;
}

export type TransactionPage = PageResponse<Transaction>;

export interface TransactionFieldErrors {
  customerId?: string;
  referenceCode?: string;
  completedAt?: string;
}
