import { describe, expect, it } from "vitest";
import { validateCustomer } from "./customer.schemas";

describe("validateCustomer", () => {
  it("requires a name and phone number", () => {
    expect(validateCustomer({ name: " ", phone: "" })).toEqual({
      name: "Customer name is required.",
      phone: "Phone number is required.",
    });
  });

  it("enforces backend name and request-length constraints", () => {
    expect(
      validateCustomer({ name: `A${"\u0000"}B`, phone: "+14155552671" }).name,
    ).toContain("control characters");
    expect(
      validateCustomer({ name: "x".repeat(121), phone: "+14155552671" }).name,
    ).toContain("120 characters");
    expect(
      validateCustomer({ name: "Valid Name", phone: "x".repeat(65) }).phone,
    ).toContain("64 characters");
  });

  it("leaves region-dependent libphonenumber validation to the backend", () => {
    expect(
      validateCustomer({ name: "Valid Name", phone: "not a phone" }),
    ).toEqual({});
  });
});
