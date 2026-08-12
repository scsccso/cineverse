import type { ErrorResponse } from "./types";

// 8081, not 8080 — see docs/DEVELOPMENT.md's port-conflict note for why the
// backend runs on 8081 in local dev. This fallback only matters when
// NEXT_PUBLIC_API_BASE_URL isn't set at all (e.g. a fresh worktree/clone
// without .env.local yet) — a wrong fallback here silently points every API
// call at a port nothing is listening on.
const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

/** Poster/backdrop URLs come back as backend-relative paths (e.g. "/uploads/x.jpg"
 * or the placeholder "/images/no-poster.svg") — they're served by the Spring
 * Boot app, not this frontend, so they need the API origin prefixed. */
export function resolveMediaUrl(path: string): string {
  return /^https?:\/\//.test(path) ? path : `${API_BASE_URL}${path}`;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: number;

  constructor(status: number, code: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

interface ApiFetchOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
}

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  const { body, headers, ...rest } = options;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    // Movies/showtimes/etc. change independently of this app's build —
    // without this, Server Component GETs with no other request-time API
    // (cookies/headers/searchParams) get statically prerendered at build
    // time and would freeze on stale data until the next deploy.
    cache: "no-store",
    // The refresh token only ever travels as an httpOnly cookie — without
    // credentials: "include" the browser neither sends it cross-origin nor
    // accepts the Set-Cookie the backend responds with.
    credentials: "include",
    headers: {
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const errorBody = data as Partial<ErrorResponse> | undefined;
    throw new ApiError(
      response.status,
      errorBody?.code ?? response.status,
      errorBody?.message ?? "Request failed",
    );
  }

  return data as T;
}
