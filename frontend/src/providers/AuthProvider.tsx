"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "../lib/api/api-error";
import {
  clearAccessToken,
  setAccessToken,
  subscribeToAuthState,
} from "../lib/auth/access-token-store";
import {
  getCurrentUser,
  loginWithGoogle,
  loginWithPassword,
  logout,
} from "../features/auth/services/auth.service";
import type { AuthUser } from "../types/auth";
import type { LoginInput } from "../features/auth/types/auth.types";

export type AuthStatus = "initializing" | "authenticated" | "unauthenticated";

export interface AuthContextValue {
  user: AuthUser | null;
  status: AuthStatus;
  isAuthenticated: boolean;
  isInitializing: boolean;
  sessionMessage: string | null;
  verificationEmail: string | null;
  registrationComplete: boolean;
  setVerificationEmail: (
    email: string,
    registrationComplete?: boolean,
  ) => void;
  clearVerificationEmail: () => void;
  signInWithPassword: (input: LoginInput) => Promise<AuthUser>;
  signInWithGoogle: (idToken: string) => Promise<AuthUser>;
  refreshCurrentUser: () => Promise<AuthUser>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [status, setStatus] = useState<AuthStatus>("initializing");
  const [sessionMessage, setSessionMessage] = useState<string | null>(null);
  const [verificationEmail, setVerificationEmailState] = useState<string | null>(
    null,
  );
  const [registrationComplete, setRegistrationComplete] = useState(false);
  const authOperation = useRef(0);
  const restoration = useRef<Promise<AuthUser> | null>(null);

  useEffect(() => {
    let active = true;
    const operation = authOperation.current;
    const unsubscribe = subscribeToAuthState((change) => {
      if (change.type === "token-cleared" || change.type === "session-expired") {
        authOperation.current += 1;
        setUser(null);
        setStatus("unauthenticated");
        setSessionMessage(
          change.type === "session-expired"
            ? "Your session could not be restored. Please sign in again."
            : null,
        );
      }
    });

    restoration.current ??= getCurrentUser();
    void restoration.current
      .then((currentUser) => {
        if (active && authOperation.current === operation) {
          setUser(currentUser);
          setStatus("authenticated");
          setSessionMessage(null);
        }
      })
      .catch(() => {
        if (active && authOperation.current === operation) {
          clearAccessToken();
          setUser(null);
          setStatus("unauthenticated");
          setSessionMessage("Your session could not be restored. Please sign in.");
        }
      });

    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const setVerificationEmail = useCallback(
    (email: string, didRegister = false) => {
      setVerificationEmailState(email.trim());
      setRegistrationComplete(didRegister);
    },
    [],
  );

  const clearVerificationEmail = useCallback(() => {
    setVerificationEmailState(null);
    setRegistrationComplete(false);
  }, []);

  const signInWithPassword = useCallback(async (input: LoginInput) => {
    clearAccessToken();
    const operation = ++authOperation.current;
    setUser(null);
    setStatus("unauthenticated");
    setSessionMessage(null);
    const response = await loginWithPassword(input);
    if (authOperation.current !== operation) {
      throw new ApiError("REQUEST_ABORTED", "The sign-in request was cancelled.");
    }
    setAccessToken(response.accessToken);
    setUser(response.user);
    setStatus("authenticated");
    setVerificationEmailState(null);
    setRegistrationComplete(false);
    return response.user;
  }, []);

  const signInWithGoogle = useCallback(async (idToken: string) => {
    clearAccessToken();
    const operation = ++authOperation.current;
    setUser(null);
    setStatus("unauthenticated");
    setSessionMessage(null);
    const response = await loginWithGoogle(idToken);
    if (authOperation.current !== operation) {
      throw new ApiError("REQUEST_ABORTED", "The sign-in request was cancelled.");
    }
    setAccessToken(response.accessToken);
    setUser(response.user);
    setStatus("authenticated");
    setVerificationEmailState(null);
    setRegistrationComplete(false);
    return response.user;
  }, []);

  const refreshCurrentUser = useCallback(async () => {
    const operation = authOperation.current;
    const currentUser = await getCurrentUser();
    if (authOperation.current !== operation) {
      throw new ApiError("REQUEST_ABORTED", "The session update was cancelled.");
    }
    setUser(currentUser);
    setStatus("authenticated");
    setSessionMessage(null);
    return currentUser;
  }, []);

  const logoutUser = useCallback(async () => {
    authOperation.current += 1;
    setUser(null);
    setStatus("unauthenticated");
    setSessionMessage(null);
    setVerificationEmailState(null);
    setRegistrationComplete(false);
    clearAccessToken();
    let serverLogoutConfirmed = true;
    try {
      await logout();
    } catch (cause) {
      serverLogoutConfirmed = false;
      throw cause;
    } finally {
      router.replace(
        serverLogoutConfirmed ? "/login" : "/login?logout=unconfirmed",
      );
    }
  }, [router]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      status,
      isAuthenticated: status === "authenticated",
      isInitializing: status === "initializing",
      sessionMessage,
      verificationEmail,
      registrationComplete,
      setVerificationEmail,
      clearVerificationEmail,
      signInWithPassword,
      signInWithGoogle,
      refreshCurrentUser,
      logout: logoutUser,
    }),
    [
      user,
      status,
      sessionMessage,
      verificationEmail,
      registrationComplete,
      setVerificationEmail,
      clearVerificationEmail,
      signInWithPassword,
      signInWithGoogle,
      refreshCurrentUser,
      logoutUser,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuthContext(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider.");
  }
  return context;
}
