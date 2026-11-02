export interface BusinessProfile {
  id: string;
  name: string;
  category: string;
  address: string;
  timezone: string;
  googleReviewUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BusinessInput {
  name: string;
  category: string;
  address: string;
  timezone: string;
  googleReviewUrl: string | null;
}

export interface GoogleReviewUrlInput {
  googleReviewUrl: string;
}

export type BusinessFieldErrors = Partial<
  Record<"name" | "category" | "address" | "timezone" | "googleReviewUrl", string>
>;

export type BusinessLoadStatus = "loading" | "ready" | "missing" | "error";
