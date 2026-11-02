const AUTH_ROUTES = new Set(["/login", "/register", "/verify-email"]);
const INTERNAL_ORIGIN = "https://review-engine.invalid";

export function getSafeReturnTo(value: string | null): string | null {
  if (!value || !value.startsWith("/") || value.startsWith("//")) {
    return null;
  }

  try {
    const url = new URL(value, INTERNAL_ORIGIN);
    if (url.origin !== INTERNAL_ORIGIN || AUTH_ROUTES.has(url.pathname)) {
      return null;
    }
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return null;
  }
}
