import { describe, expect, it } from "vitest";
import {
  validateEmail,
  validateLogin,
  validateRegistration,
  validateVerification,
} from "./auth.schemas";

describe("authentication form validation", () => {
  it("requires registration fields and validates email and password rules", () => {
    expect(
      validateRegistration({
        name: "",
        email: "not-an-email",
        password: "short",
        confirmPassword: "different",
      }),
    ).toEqual({
      name: "Name is required.",
      email: "Enter a valid email address.",
      password: "Password must be between 12 and 72 characters.",
      confirmPassword: "Passwords do not match.",
    });
  });

  it("accepts backend-supported password lengths and matching confirmation", () => {
    expect(
      validateRegistration({
        name: "Review Owner",
        email: "owner@example.com",
        password: "a-secure-pass",
        confirmPassword: "a-secure-pass",
      }),
    ).toEqual({});
    expect(validateRegistration({
      name: "A".repeat(121),
      email: "owner@example.com",
      password: "a".repeat(73),
      confirmPassword: "a".repeat(73),
    })).toMatchObject({
      name: "Name must be 120 characters or fewer.",
      password: "Password must be between 12 and 72 characters.",
    });
  });

  it("validates login email and required password", () => {
    expect(validateLogin({ email: "", password: "  " })).toEqual({
      email: "Email is required.",
      password: "Password is required.",
    });
  });

  it("requires a valid email and six-digit OTP", () => {
    expect(validateVerification({ email: "owner@example.com", otp: "12345x" }))
      .toEqual({ otp: "Enter the six-digit verification code." });
    expect(validateEmail("owner@example.com")).toBeUndefined();
  });
});
