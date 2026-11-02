import type {
  BusinessFieldErrors,
  BusinessInput,
} from "../types/business.types";

const GOOGLE_REVIEW_HOSTS = new Set([
  "g.page",
  "maps.app.goo.gl",
  "goo.gl",
  "www.google.com",
  "google.com",
  "search.google.com",
  "maps.google.com",
]);

export function validateGoogleReviewUrl(value: string): string | undefined {
  const normalized = value.trim();
  if (!normalized) {
    return undefined;
  }
  if (normalized.length > 2048) {
    return "Google review URL must be 2048 characters or fewer.";
  }

  try {
    const url = new URL(normalized);
    const host = url.hostname.toLowerCase();
    if (
      url.protocol !== "https:" ||
      !GOOGLE_REVIEW_HOSTS.has(host) ||
      (url.port !== "" && url.port !== "443") ||
      url.username.length > 0 ||
      url.password.length > 0 ||
      !isSupportedReviewPath(host, url.pathname, url.search)
    ) {
      return "Enter a supported HTTPS Google review URL.";
    }
    return undefined;
  } catch {
    return "Enter a valid HTTPS Google review URL.";
  }
}

function isSupportedReviewPath(host: string, pathname: string, search: string) {
  const params = new URLSearchParams(search);
  switch (host) {
    case "g.page":
      return /^\/r\/[^/]+\/review\/?$/.test(pathname);
    case "maps.app.goo.gl":
      return pathname !== "/" && pathname.length > 0;
    case "goo.gl":
      return pathname.startsWith("/maps/") && pathname.length > 6;
    case "search.google.com":
      return (
        pathname === "/local/writereview" &&
        Boolean(params.get("placeid"))
      );
    case "google.com":
    case "www.google.com":
    case "maps.google.com":
      return (
        (pathname === "/maps" || pathname.startsWith("/maps/place/")) &&
        (Boolean(params.get("cid")) ||
          Boolean(params.get("placeid")) ||
          pathname.startsWith("/maps/place/"))
      );
    default:
      return false;
  }
}

export function validateBusiness(
  input: BusinessInput,
): BusinessFieldErrors {
  const errors: BusinessFieldErrors = {};
  const fields: Array<{
    key: "name" | "category" | "address" | "timezone";
    value: string;
    label: string;
    max: number;
  }> = [
    { key: "name", value: input.name, label: "Business name", max: 160 },
    { key: "category", value: input.category, label: "Category", max: 100 },
    { key: "address", value: input.address, label: "Address", max: 500 },
    { key: "timezone", value: input.timezone, label: "Time zone", max: 64 },
  ];

  for (const field of fields) {
    if (!field.value.trim()) {
      errors[field.key] = `${field.label} is required.`;
    } else if (field.value.trim().length > field.max) {
      errors[field.key] = `${field.label} must be ${field.max} characters or fewer.`;
    }
  }

  if (input.timezone.trim() && !isValidTimezone(input.timezone.trim())) {
    errors.timezone = "Enter a valid IANA time zone.";
  }

  const reviewUrlError = validateGoogleReviewUrl(input.googleReviewUrl ?? "");
  if (reviewUrlError) {
    errors.googleReviewUrl = reviewUrlError;
  }
  return errors;
}

function isValidTimezone(value: string): boolean {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: value });
    return true;
  } catch {
    return false;
  }
}
