import { apiFetch } from "./client";
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UserResponse,
} from "./types";

export function registerUser(payload: RegisterRequest): Promise<UserResponse> {
  return apiFetch<UserResponse>("/api/v1/auth/register", {
    method: "POST",
    body: payload,
  });
}

export function loginUser(payload: LoginRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/v1/auth/login", {
    method: "POST",
    body: payload,
  });
}

/** Relies solely on the httpOnly refresh_token cookie sent by the browser. */
export function refreshSession(): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/v1/auth/refresh", {
    method: "POST",
  });
}

export function logoutUser(): Promise<void> {
  return apiFetch<void>("/api/v1/auth/logout", {
    method: "POST",
  });
}

export function getCurrentUser(accessToken: string): Promise<UserResponse> {
  return apiFetch<UserResponse>("/api/v1/users/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}
