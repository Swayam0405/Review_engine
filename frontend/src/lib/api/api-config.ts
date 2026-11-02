import { ApiError } from "./api-error";

export function getApiBaseUrl(): string {
  const configuredUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();
  if (!configuredUrl) {
    throw new ApiError(
      "CONFIGURATION_ERROR",
      "The API server is not configured.",
    );
  }

  let parsedUrl: URL;
  try {
    parsedUrl = new URL(configuredUrl);
  } catch {
    throw new ApiError(
      "CONFIGURATION_ERROR",
      "The API server URL is invalid.",
    );
  }

  if (
    !["http:", "https:"].includes(parsedUrl.protocol) ||
    parsedUrl.username.length > 0 ||
    parsedUrl.password.length > 0 ||
    parsedUrl.search.length > 0 ||
    parsedUrl.hash.length > 0
  ) {
    throw new ApiError(
      "CONFIGURATION_ERROR",
      "The API server URL must be an HTTP(S) base URL without credentials.",
    );
  }

  return parsedUrl.toString().replace(/\/+$/, "");
}

export function resolveApiUrl(path: string): string {
  if (
    path.length === 0 ||
    path.startsWith("//") ||
    /^[a-z][a-z\d+.-]*:/i.test(path)
  ) {
    throw new ApiError(
      "CONFIGURATION_ERROR",
      "API requests must use a relative endpoint path.",
    );
  }

  const baseUrl = getApiBaseUrl();
  try {
    return new URL(path.replace(/^\/+/, ""), `${baseUrl}/`).toString();
  } catch {
    throw new ApiError(
      "CONFIGURATION_ERROR",
      "The API endpoint URL is invalid.",
    );
  }
}
