// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import type { ReactNode } from "react";
import { ApiError } from "../../../lib/api/api-error";
import { TransactionDetailsPage } from "./TransactionDetailsPage";
import { TransactionFormPage } from "./TransactionFormPage";
import { TransactionListPage } from "./TransactionListPage";

const mocks = vi.hoisted(() => ({
  listTransactions: vi.fn(),
  getTransaction: vi.fn(),
  createTransaction: vi.fn(),
  listCustomers: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
}));

vi.mock("next/link", async () => {
  const React = await import("react");
  return {
    default: ({
      href,
      children,
      ...props
    }: {
      href: string;
      children: ReactNode;
      [key: string]: unknown;
    }) => React.createElement("a", { ...props, href }, children),
  };
});

vi.mock("../../business/components/BusinessRouteGate", () => ({
  BusinessRouteGate: ({ children }: { children: ReactNode }) => children,
}));

vi.mock("../services/transaction.service", () => ({
  listTransactions: mocks.listTransactions,
  getTransaction: mocks.getTransaction,
  createTransaction: mocks.createTransaction,
}));

vi.mock("../../customers/services/customer.service", () => ({
  listCustomers: mocks.listCustomers,
}));

const transaction = {
  id: "transaction-uuid",
  customerId: "customer-uuid",
  customerName: "Alex Customer",
  referenceCode: "ORDER-12",
  status: "COMPLETED" as const,
  completedAt: "2026-10-31T12:00:00Z",
  createdAt: "2026-10-31T12:00:01Z",
  updatedAt: "2026-10-31T12:00:01Z",
};

const transactionPage = {
  content: [transaction],
  pageNumber: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

const customerPage = {
  content: [
    {
      id: "customer-uuid",
      name: "Alex Customer",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z",
    },
  ],
  pageNumber: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.listTransactions.mockResolvedValue(transactionPage);
  mocks.getTransaction.mockResolvedValue(transaction);
  mocks.createTransaction.mockResolvedValue(transaction);
  mocks.listCustomers.mockResolvedValue(customerPage);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("transaction list", () => {
  it("shows loading state, renders actual data and paginates", async () => {
    const pageWithNext = { ...transactionPage, totalPages: 2, hasNext: true };
    mocks.listTransactions.mockResolvedValue(pageWithNext);
    render(<TransactionListPage />);
    expect(screen.getByRole("status", { name: "Loading transactions" })).toBeTruthy();
    expect(await screen.findByText("Alex Customer")).toBeTruthy();
    expect(screen.getByText("COMPLETED")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Add transaction" })).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() =>
      expect(mocks.listTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1 }),
      ),
    );
  });

  it("supports reference-code filtering, empty state and retry after errors", async () => {
    mocks.listTransactions
      .mockResolvedValueOnce(transactionPage)
      .mockResolvedValueOnce({ ...transactionPage, content: [], totalElements: 0, totalPages: 0 })
      .mockRejectedValueOnce(new ApiError("NETWORK_ERROR", "Offline.", 503))
      .mockResolvedValueOnce(transactionPage);
    render(<TransactionListPage />);
    await screen.findByText("Alex Customer");
    fireEvent.change(screen.getByRole("searchbox"), {
      target: { value: "MISSING" },
    });
    expect(await screen.findByText("No transactions match “MISSING”.")).toBeTruthy();
    expect(mocks.listTransactions).toHaveBeenLastCalledWith(
      expect.objectContaining({ referenceCode: "MISSING" }),
    );

    fireEvent.change(screen.getByRole("searchbox"), {
      target: { value: "FAIL" },
    });
    expect((await screen.findByRole("alert")).textContent).toBe("Offline.");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("Alex Customer")).toBeTruthy();
  });
});

describe("transaction creation and customer selection", () => {
  it("loads paginated customers and submits only customerId and optional reference", async () => {
    render(<TransactionFormPage />);
    expect(await screen.findByRole("option", { name: "Alex Customer" })).toBeTruthy();
    fireEvent.change(screen.getByLabelText("Customer *"), {
      target: { value: "customer-uuid" },
    });
    fireEvent.change(screen.getByLabelText("Reference code (optional)"), {
      target: { value: " ORDER-12 " },
    });
    fireEvent.change(screen.getByLabelText("Completion date and time (optional)"), {
      target: { value: "2026-10-31T12:00" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Record completed transaction" }),
    );

    await waitFor(() =>
      expect(mocks.createTransaction).toHaveBeenCalledWith({
        customerId: "customer-uuid",
        referenceCode: "ORDER-12",
        completedAt: new Date("2026-10-31T12:00").toISOString(),
      }),
    );
    expect(mocks.replace).toHaveBeenCalledWith(
      "/transactions/transaction-uuid?created=1",
    );
    expect(mocks.createTransaction.mock.calls[0]?.[0]).not.toHaveProperty("status");
    expect(mocks.createTransaction.mock.calls[0]?.[0]).not.toHaveProperty("businessId");
  });

  it("searches and pages through customer results instead of fetching the whole directory", async () => {
    mocks.listCustomers
      .mockResolvedValueOnce({
        ...customerPage,
        totalPages: 2,
        hasNext: true,
      })
      .mockResolvedValueOnce({
        ...customerPage,
        content: [
          {
            ...customerPage.content[0],
            id: "search-customer",
            name: "Second Customer",
          },
        ],
        totalElements: 21,
        totalPages: 2,
        hasNext: true,
      })
      .mockResolvedValueOnce({
        ...customerPage,
        content: [{ ...customerPage.content[0], id: "another-customer", name: "Second Customer" }],
        pageNumber: 1,
        totalElements: 21,
        totalPages: 2,
        hasNext: false,
        hasPrevious: true,
      });
    render(<TransactionFormPage />);
    fireEvent.change(await screen.findByRole("searchbox", { name: "Find a customer" }), {
      target: { value: "Second" },
    });
    await screen.findByRole("option", { name: "Second Customer" });
    fireEvent.click(screen.getByRole("button", { name: "More customers" }));
    fireEvent.change(await screen.findByLabelText("Customer *"), {
      target: { value: "another-customer" },
    });
    expect(mocks.listCustomers).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: "Second", page: 1 }),
    );
    expect((screen.getByLabelText("Customer *") as HTMLSelectElement).value)
      .toBe("another-customer");
  });

  it("prevents submission without a customer and reports customer loading state", async () => {
    mocks.listCustomers.mockImplementation(
      () => new Promise(() => undefined),
    );
    render(<TransactionFormPage />);
    expect(await screen.findByRole("status")).toBeTruthy();
    const submit = screen.getByRole("button", {
      name: "Record completed transaction",
    });
    fireEvent.click(submit);
    expect(mocks.createTransaction).not.toHaveBeenCalled();
    expect(await screen.findByText("Select a customer.")).toBeTruthy();
  });

  it("shows empty and error states from the customer picker", async () => {
    mocks.listCustomers.mockResolvedValueOnce({
      ...customerPage,
      content: [],
      totalElements: 0,
      totalPages: 0,
    });
    const firstRender = render(<TransactionFormPage />);
    expect(await screen.findByText(
      "No customers yet. Add a customer before recording a transaction.",
    )).toBeTruthy();
    firstRender.unmount();

    mocks.listCustomers.mockRejectedValue(
      new ApiError("NETWORK_ERROR", "Customers offline.", 503),
    );
    render(<TransactionFormPage />);
    expect((await screen.findByRole("alert")).textContent).toBe("Customers offline.");
  });

  it("shows recoverable and field-level backend submission errors", async () => {
    mocks.createTransaction.mockRejectedValueOnce(
      new ApiError("CONFLICT", "A transaction with this reference code already exists.", 409),
    );
    render(<TransactionFormPage />);
    fireEvent.change(await screen.findByLabelText("Customer *"), {
      target: { value: "customer-uuid" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Record completed transaction" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "reference code already exists",
    );
    expect((screen.getByLabelText("Customer *") as HTMLSelectElement).value)
      .toBe("customer-uuid");

    mocks.createTransaction.mockRejectedValueOnce(
      new ApiError("BAD_REQUEST", "Invalid value for field: referenceCode.", 400),
    );
    fireEvent.change(screen.getByLabelText("Reference code (optional)"), {
      target: { value: "ref" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Record completed transaction" }));
    expect(await screen.findByText("Enter a valid reference code.")).toBeTruthy();
  });

  it("prevents duplicate transaction submission while the create request is pending", async () => {
    let finishCreate: ((result: typeof transaction) => void) | undefined;
    mocks.createTransaction.mockImplementation(
      () => new Promise((resolve) => (finishCreate = resolve)),
    );
    render(<TransactionFormPage />);
    fireEvent.change(await screen.findByLabelText("Customer *"), {
      target: { value: "customer-uuid" },
    });
    const form = screen
      .getByRole("button", { name: "Record completed transaction" })
      .closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(mocks.createTransaction).toHaveBeenCalledTimes(1);
    finishCreate?.(transaction);
  });
});

describe("transaction details", () => {
  it("renders transaction data and links to the associated customer", async () => {
    render(<TransactionDetailsPage transactionId={transaction.id} created />);
    expect(
      await screen.findByRole("heading", { name: "Completed transaction" }),
    ).toBeTruthy();
    expect(screen.getByText("ORDER-12")).toBeTruthy();
    expect(screen.getByText("COMPLETED")).toBeTruthy();
    expect(screen.getByRole("status").textContent).toContain("recorded successfully");
    expect(screen.getByRole("link", { name: "Alex Customer" }).getAttribute("href"))
      .toBe("/customers/customer-uuid");
  });

  it("handles not-found transaction state", async () => {
    mocks.getTransaction.mockRejectedValue(
      new ApiError("NOT_FOUND", "The requested resource was not found.", 404),
    );
    render(<TransactionDetailsPage transactionId="missing-id" created={false} />);
    expect(await screen.findByRole("heading", { name: "Transaction not found" }))
      .toBeTruthy();
  });
});
