import { apiRequest } from "../../../lib/api/api-client";
import { ApiError } from "../../../lib/api/api-error";
import type {
  BusinessInput,
  BusinessProfile,
  GoogleReviewUrlInput,
} from "../types/business.types";

const BUSINESS_PATH = "/api/business";

export async function createBusiness(
  input: BusinessInput,
): Promise<BusinessProfile> {
  return parseBusinessProfile(
    await apiRequest<unknown>(BUSINESS_PATH, {
      method: "POST",
      body: input,
      auth: "required",
    }),
  );
}

export async function getMyBusiness(
  signal?: AbortSignal,
): Promise<BusinessProfile> {
  return parseBusinessProfile(
    await apiRequest<unknown>(`${BUSINESS_PATH}/me`, {
      auth: "required",
      signal,
    }),
  );
}

export async function updateBusiness(
  input: BusinessInput,
): Promise<BusinessProfile> {
  return parseBusinessProfile(
    await apiRequest<unknown>(`${BUSINESS_PATH}/me`, {
      method: "PUT",
      body: input,
      auth: "required",
    }),
  );
}

export async function updateGoogleReviewUrl(
  input: GoogleReviewUrlInput,
): Promise<BusinessProfile> {
  return parseBusinessProfile(
    await apiRequest<unknown>(`${BUSINESS_PATH}/me/google-review-url`, {
      method: "PATCH",
      body: input,
      auth: "required",
    }),
  );
}

export async function getGoogleReviewQr(
  signal?: AbortSignal,
): Promise<Blob> {
  const image = await apiRequest<Blob>(
    `${BUSINESS_PATH}/me/google-review-qr`,
    {
      auth: "required",
      responseType: "blob",
      signal,
    },
  );
  if (!(image instanceof Blob) || image.type !== "image/png") {
    throw new ApiError(
      "INVALID_RESPONSE",
      "The server returned an invalid QR code image.",
    );
  }
  return image;
}

function parseBusinessProfile(value: unknown): BusinessProfile {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.name !== "string" ||
    typeof value.category !== "string" ||
    typeof value.address !== "string" ||
    typeof value.timezone !== "string" ||
    !(value.googleReviewUrl === null || typeof value.googleReviewUrl === "string") ||
    typeof value.createdAt !== "string" ||
    typeof value.updatedAt !== "string"
  ) {
    throw new ApiError(
      "INVALID_RESPONSE",
      "The server returned an invalid business profile.",
    );
  }
  return {
    id: value.id,
    name: value.name,
    category: value.category,
    address: value.address,
    timezone: value.timezone,
    googleReviewUrl: value.googleReviewUrl,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
