export type AuthStateChange =
  | { type: "token-set" }
  | { type: "token-cleared" }
  | { type: "session-expired" };

type AuthStateListener = (change: AuthStateChange) => void;

// Access tokens live only in this module's runtime memory; never persist them.
let accessToken: string | null = null;
const listeners = new Set<AuthStateListener>();

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string): void {
  const normalizedToken = token.trim();
  if (normalizedToken.length === 0) {
    throw new TypeError("An access token must not be empty.");
  }

  accessToken = normalizedToken;
  publish({ type: "token-set" });
}

export function clearAccessToken(): void {
  const hadToken = accessToken !== null;
  accessToken = null;
  if (hadToken) {
    publish({ type: "token-cleared" });
  }
}

export function expireAccessToken(): void {
  accessToken = null;
  publish({ type: "session-expired" });
}

export function subscribeToAuthState(
  listener: AuthStateListener,
): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function publish(change: AuthStateChange): void {
  for (const listener of listeners) {
    listener(change);
  }
}
