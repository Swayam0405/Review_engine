import type { PageResponse } from "../../../types/api";

export interface CustomerSummary {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface Customer extends CustomerSummary {
  phone: string;
}

export interface CustomerInput {
  name: string;
  phone: string;
}

export type CustomerPage = PageResponse<CustomerSummary>;

export interface CustomerFieldErrors {
  name?: string;
  phone?: string;
}
