import { describe, expect, it } from "vitest";
import {
  validateBusiness,
  validateGoogleReviewUrl,
} from "./business.schemas";

describe("business schemas", () => {
  it("requires supported business fields and validates timezone", () => {
    expect(
      validateBusiness({
        name: "",
        category: "",
        address: "",
        timezone: "Not/AZone",
        googleReviewUrl: null,
      }),
    ).toEqual({
      name: "Business name is required.",
      category: "Category is required.",
      address: "Address is required.",
      timezone: "Enter a valid IANA time zone.",
    });
  });

  it("accepts backend field limits, a real IANA timezone, and no optional review URL", () => {
    expect(
      validateBusiness({
        name: "Review Shop",
        category: "Retail",
        address: "1 Main Street",
        timezone: "Asia/Kolkata",
        googleReviewUrl: null,
      }),
    ).toEqual({});
    expect(
      validateBusiness({
        name: "B".repeat(161),
        category: "C".repeat(101),
        address: "A".repeat(501),
        timezone: "UTC",
        googleReviewUrl: null,
      }),
    ).toMatchObject({
      name: "Business name must be 160 characters or fewer.",
      category: "Category must be 100 characters or fewer.",
      address: "Address must be 500 characters or fewer.",
    });
  });

  it.each([
    "https://g.page/r/abc123/review",
    "https://maps.app.goo.gl/abc123",
    "https://search.google.com/local/writereview?placeid=place123",
    "https://www.google.com/maps/place/Example",
  ])("accepts backend-supported Google review URL %s", (url) => {
    expect(validateGoogleReviewUrl(url)).toBeUndefined();
  });

  it.each([
    "javascript:alert(1)",
    "http://g.page/r/abc/review",
    "https://attacker.example/review",
    "https://g.page/not-a-review",
    "https://user:pass@g.page/r/abc/review",
  ])("rejects unsupported review URL %s", (url) => {
    expect(validateGoogleReviewUrl(url)).toBeDefined();
  });
});
