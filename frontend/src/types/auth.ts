export type AccountStatus = "PENDING_VERIFICATION" | "ACTIVE" | "DISABLED";

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  accountStatus: AccountStatus;
  emailVerified: boolean;
  hasBusiness: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: AuthUser;
}
