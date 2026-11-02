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
import { CustomerDetailsPage } from "./CustomerDetailsPage";
import { CustomerFormPage } from "./CustomerFormPage";
import { CustomerListPage } from "./CustomerListPage";

const mocks = vi.hoisted(() => ({
  listCustomers: vi.fn(),
  getCustomer: vi.fn(),
  createCustomer: vi.fn(),
  updateCustomer: vi.fn(),
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

vi.mock("../services/customer.service", () => ({
  listCustomers: mocks.listCustomers,
  getCustomer: mocks.getCustomer,
  createCustomer: mocks.createCustomer,
  updateCustomer: mocks.updateCustomer,
}));

const customer = {
  id: "customer-uuid",
  name: "Alex Customer",
  phone: "+14155552671",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

const customerPage = {
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

function fillCustomerForm(name = customer.name, phone = customer.phone) {
  fireEvent.change(screen.getByLabelText("Customer name *"), {
    target: { value: name },
  });
  fireEvent.change(screen.getByLabelText("Phone number *"), {
    target: { value: phone },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.listCustomers.mockResolvedValue(customerPage);
  mocks.getCustomer.mockResolvedValue(customer);
  mocks.createCustomer.mockResolvedValue(customer);
  mocks.updateCustomer.mockResolvedValue({ ...customer, name: "Updated Alex" });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("customer list", () => {
  it("renders customer summaries without phone data", async () => {
    render(<CustomerListPage />);
    expect(await screen.findByText("Alex Customer")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Customers" })).toBeTruthy();
    expect(screen.getByRole("link", { name: "Add customer" })).toBeTruthy();
    expect(screen.queryByText(customer.phone)).toBeNull();
  });

  it("debounces supported name search and displays a no-match state", async () => {
    mocks.listCustomers
      .mockResolvedValueOnce(customerPage)
      .mockResolvedValueOnce({ ...customerPage, content: [], totalElements: 0, totalPages: 0 });
    render(<CustomerListPage />);
    await screen.findByText("Alex Customer");
    fireEvent.change(screen.getByRole("searchbox"), {
      target: { value: "Nobody" },
    });

    expect(await screen.findByText("No customers match “Nobody”.")).toBeTruthy();
    expect(mocks.listCustomers).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: "Nobody", page: 0 }),
    );
  });

  it("shows loading, handles failures, and retries", async () => {
    let finishLoad: ((value: typeof customerPage) => void) | undefined;
    mocks.listCustomers
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishLoad = resolve;
          }),
      )
      .mockRejectedValueOnce(new ApiError("NETWORK_ERROR", "Offline.", 503))
      .mockResolvedValueOnce(customerPage);
    render(<CustomerListPage />);

    expect(screen.getByRole("status", { name: "Loading customers" })).toBeTruthy();
    await waitFor(() => expect(mocks.listCustomers).toHaveBeenCalledTimes(1));
    finishLoad?.(customerPage);
    await screen.findByText("Alex Customer");

    fireEvent.change(screen.getByRole("searchbox"), {
      target: { value: "Retry" },
    });
    expect((await screen.findByRole("alert")).textContent).toBe("Offline.");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("Alex Customer")).toBeTruthy();
  });
});

describe("customer creation and editing", () => {
  it("validates required fields without sending a request", async () => {
    render(<CustomerFormPage mode="create" />);
    fireEvent.click(screen.getByRole("button", { name: "Create customer" }));
    expect(await screen.findByText("Customer name is required.")).toBeTruthy();
    expect(screen.getByText("Phone number is required.")).toBeTruthy();
    expect(mocks.createCustomer).not.toHaveBeenCalled();
  });

  it("creates a customer with only supported fields and navigates to details", async () => {
    render(<CustomerFormPage mode="create" />);
    fillCustomerForm();
    fireEvent.click(screen.getByRole("button", { name: "Create customer" }));

    await waitFor(() =>
      expect(mocks.createCustomer).toHaveBeenCalledWith({
        name: "Alex Customer",
        phone: "+14155552671",
      }),
    );
    expect(mocks.replace).toHaveBeenCalledWith(
      "/customers/customer-uuid?created=1",
    );
  });

  it("shows backend invalid-phone errors inline and preserves entered values", async () => {
    mocks.createCustomer.mockRejectedValue(
      new ApiError("BAD_REQUEST", "A valid phone number is required.", 400),
    );
    render(<CustomerFormPage mode="create" />);
    fillCustomerForm("Alex Customer", "not a phone");
    fireEvent.click(screen.getByRole("button", { name: "Create customer" }));

    expect(await screen.findByText("Enter a valid phone number.")).toBeTruthy();
    expect((screen.getByLabelText("Phone number *") as HTMLInputElement).value)
      .toBe("not a phone");
    expect(mocks.createCustomer).toHaveBeenCalledTimes(1);
  });

  it("prevents duplicate submissions while create is pending", async () => {
    let finishCreate: ((value: typeof customer) => void) | undefined;
    mocks.createCustomer.mockImplementation(
      () => new Promise((resolve) => (finishCreate = resolve)),
    );
    render(<CustomerFormPage mode="create" />);
    fillCustomerForm();
    const form = screen.getByRole("button", { name: "Create customer" }).closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(mocks.createCustomer).toHaveBeenCalledTimes(1);
    finishCreate?.(customer);
  });

  it("loads before edit, updates, and navigates to refreshed details", async () => {
    render(<CustomerFormPage customerId={customer.id} mode="edit" />);
    expect(await screen.findByDisplayValue("Alex Customer")).toBeTruthy();
    expect(mocks.getCustomer).toHaveBeenCalledWith(
      customer.id,
      expect.any(AbortSignal),
    );
    fillCustomerForm("Updated Alex", customer.phone);
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(mocks.updateCustomer).toHaveBeenCalledWith(customer.id, {
        name: "Updated Alex",
        phone: customer.phone,
      }),
    );
    expect(mocks.replace).toHaveBeenCalledWith(
      "/customers/customer-uuid?updated=1",
    );
  });
});

describe("customer details", () => {
  it("loads customer detail and presents an edit action", async () => {
    render(
      <CustomerDetailsPage
        customerId={customer.id}
        notice="Customer updated successfully."
      />,
    );
    expect(await screen.findByRole("heading", { name: "Alex Customer" })).toBeTruthy();
    expect(screen.getByText(customer.phone)).toBeTruthy();
    expect(screen.getByRole("status").textContent).toBe(
      "Customer updated successfully.",
    );
    expect(screen.getByRole("link", { name: "Edit customer" }).getAttribute("href"))
      .toBe("/customers/customer-uuid/edit");
  });

  it("shows a clear not-found state", async () => {
    mocks.getCustomer.mockRejectedValue(
      new ApiError("NOT_FOUND", "The requested resource was not found.", 404),
    );
    render(<CustomerDetailsPage customerId="missing-id" />);
    expect(await screen.findByRole("heading", { name: "Customer not found" }))
      .toBeTruthy();
  });
});
