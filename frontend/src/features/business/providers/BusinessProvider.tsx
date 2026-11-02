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
import { ApiError } from "../../../lib/api/api-error";
import { useAuth } from "../../auth/hooks/useAuth";
import type { AuthStatus } from "../../../providers/AuthProvider";
import { getMyBusiness } from "../services/business.service";
import type {
  BusinessLoadStatus,
  BusinessProfile,
} from "../types/business.types";

interface BusinessContextValue {
  business: BusinessProfile | null;
  status: BusinessLoadStatus;
  error: string | null;
  refreshBusiness: () => Promise<BusinessProfile>;
  retryBusinessLoad: () => void;
}

const BusinessContext = createContext<BusinessContextValue | null>(null);

export function BusinessProvider({ children }: { children: ReactNode }) {
  const { status: authStatus } = useAuth();
  const [business, setBusiness] = useState<BusinessProfile | null>(null);
  const [loadResult, setLoadResult] = useState<{
    authStatus: AuthStatus;
    requestId: number;
    status: Exclude<BusinessLoadStatus, "loading">;
    error: string | null;
  } | null>(null);
  const [requestId, setRequestId] = useState(0);
  const requestVersion = useRef(0);

  useEffect(() => {
    if (authStatus !== "authenticated") {
      return;
    }

    const version = ++requestVersion.current;
    const controller = new AbortController();
    void getMyBusiness(controller.signal)
      .then((profile) => {
        if (version !== requestVersion.current) {
          return;
        }
        setBusiness(profile);
        setLoadResult({
          authStatus,
          requestId,
          status: "ready",
          error: null,
        });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted || version !== requestVersion.current) {
          return;
        }
        if (cause instanceof ApiError && cause.status === 404) {
          setBusiness(null);
          setLoadResult({
            authStatus,
            requestId,
            status: "missing",
            error: null,
          });
          return;
        }
        setBusiness(null);
        setLoadResult({
          authStatus,
          requestId,
          status: "error",
          error:
            cause instanceof ApiError
              ? cause.message
              : "Business details could not be loaded.",
        });
      });

    return () => controller.abort();
  }, [authStatus, requestId]);

  const resultIsCurrent =
    authStatus === "authenticated" &&
    loadResult?.authStatus === authStatus &&
    loadResult.requestId === requestId;
  const status: BusinessLoadStatus = resultIsCurrent
    ? loadResult.status
    : "loading";
  const error = resultIsCurrent ? loadResult.error : null;

  const refreshBusiness = useCallback(async () => {
    const version = ++requestVersion.current;
    try {
      const profile = await getMyBusiness();
      if (version !== requestVersion.current) {
        return profile;
      }
      setBusiness(profile);
      setLoadResult({
        authStatus: "authenticated",
        requestId,
        status: "ready",
        error: null,
      });
      return profile;
    } catch (cause) {
      if (version === requestVersion.current) {
        setBusiness(null);
        setLoadResult({
          authStatus: "authenticated",
          requestId,
          status: "error",
          error:
            cause instanceof ApiError
              ? cause.message
              : "Business details could not be loaded.",
        });
      }
      throw cause;
    }
  }, [requestId]);

  const retryBusinessLoad = useCallback(() => {
    setRequestId((current) => current + 1);
  }, []);

  const value = useMemo(
    () => ({
      business: status === "ready" ? business : null,
      status,
      error,
      refreshBusiness,
      retryBusinessLoad,
    }),
    [business, status, error, refreshBusiness, retryBusinessLoad],
  );

  return (
    <BusinessContext.Provider value={value}>
      {children}
    </BusinessContext.Provider>
  );
}

export function useBusiness(): BusinessContextValue {
  const context = useContext(BusinessContext);
  if (!context) {
    throw new Error("useBusiness must be used within BusinessProvider.");
  }
  return context;
}
