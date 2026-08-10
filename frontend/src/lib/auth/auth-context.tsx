"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { ApiError } from "@/lib/api/client";
import {
  getCurrentUser,
  loginUser,
  logoutUser,
  refreshSession,
  registerUser,
} from "@/lib/api/auth";
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UserResponse,
} from "@/lib/api/types";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  status: AuthStatus;
  user: UserResponse | null;
  login: (payload: LoginRequest) => Promise<AuthResponse>;
  register: (payload: RegisterRequest) => Promise<UserResponse>;
  logout: () => Promise<void>;
  /** Calls /api/v1/users/me, transparently refreshing the access token once on a 401. */
  fetchCurrentUser: () => Promise<UserResponse>;
  /** Runs fn with a valid bearer token, transparently refreshing once on a 401 — the
   * same retry logic fetchCurrentUser uses, generalized for other authenticated calls
   * (e.g. bookings) that don't have their own dedicated context method. */
  callAuthorized: <T>(fn: (accessToken: string) => Promise<T>) => Promise<T>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<UserResponse | null>(null);

  // Access token lives only in memory (never localStorage, to keep it out of
  // XSS's reach) and only ever this ref is read inside callbacks, so it's
  // never stale even when a caller runs right after a state update.
  const accessTokenRef = useRef<string | null>(null);
  // Single-flight guard: concurrent 401s must trigger exactly one
  // /auth/refresh call, with every caller awaiting that same promise.
  const refreshInFlightRef = useRef<Promise<AuthResponse> | null>(null);

  const applySession = useCallback((session: AuthResponse) => {
    accessTokenRef.current = session.accessToken;
    setUser(session.user);
    setStatus("authenticated");
  }, []);

  const clearSession = useCallback(() => {
    accessTokenRef.current = null;
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  const refresh = useCallback((): Promise<AuthResponse> => {
    if (refreshInFlightRef.current) {
      return refreshInFlightRef.current;
    }

    const promise = refreshSession()
      .then((session) => {
        applySession(session);
        return session;
      })
      .catch((error) => {
        clearSession();
        throw error;
      })
      .finally(() => {
        refreshInFlightRef.current = null;
      });

    refreshInFlightRef.current = promise;
    return promise;
  }, [applySession, clearSession]);

  // Access token is memory-only, so a hard reload always starts with none.
  // The httpOnly refresh_token cookie (if any) is the only thing that can
  // silently restore the session — this is the real proof the refresh
  // cookie plumbing works end to end.
  useEffect(() => {
    refresh().catch(() => {
      // No valid refresh cookie yet (e.g. first-time visitor) — not an error.
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(
    async (payload: LoginRequest): Promise<AuthResponse> => {
      const session = await loginUser(payload);
      applySession(session);
      return session;
    },
    [applySession],
  );

  const register = useCallback((payload: RegisterRequest) => {
    return registerUser(payload);
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutUser();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const callAuthorized = useCallback(
    async <T,>(fn: (accessToken: string) => Promise<T>): Promise<T> => {
      if (accessTokenRef.current) {
        try {
          return await fn(accessTokenRef.current);
        } catch (error) {
          if (!(error instanceof ApiError) || error.status !== 401) {
            throw error;
          }
          // fall through to refresh-and-retry below
        }
      }

      const session = await refresh();
      return fn(session.accessToken);
    },
    [refresh],
  );

  const fetchCurrentUser = useCallback(
    () => callAuthorized((token) => getCurrentUser(token)),
    [callAuthorized],
  );

  return (
    <AuthContext.Provider
      value={{ status, user, login, register, logout, fetchCurrentUser, callAuthorized }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
