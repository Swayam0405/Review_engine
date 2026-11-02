import { describe, expect, it } from "vitest";
import { validateTransaction } from "./transaction.schemas";

describe("validateTransaction", () => {
  it("requires a selected customer", () => {
    expect(validateTransaction({ customerId: "" })).toEqual({
      customerId: "Select a customer.",
    });
  });

  it("validates optional reference code length and controls", () => {
    expect(
      validateTransaction({
        customerId: "customer-uuid",
        referenceCode: "x".repeat(101),
      }).referenceCode,
    ).toContain("100 characters");
    expect(
      validateTransaction({
        customerId: "customer-uuid",
        referenceCode: "bad\nreference",
      }).referenceCode,
    ).toContain("control characters");
    expect(
      validateTransaction({
        customerId: "customer-uuid",
        referenceCode: "   ",
      }),
    ).toEqual({});
  });

  it("validates optional offset-aware completion date and future limit", () => {
    expect(
      validateTransaction({
        customerId: "customer-uuid",
        completedAt: "not-a-date",
      }).completedAt,
    ).toBe("Enter a valid completion date and time.");
    expect(
      validateTransaction({
        customerId: "customer-uuid",
        completedAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      }).completedAt,
    ).toContain("five minutes");
  });
});
