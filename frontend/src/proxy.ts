import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const REFRESH_COOKIE_NAME = "refresh_token";

/**
 * Coarse gate only. The access token lives in memory on the client and is
 * never sent as a cookie, so Proxy — which runs on the server, before any
 * client-side React code exists — has no way to check it. The httpOnly
 * refresh_token cookie is the only session evidence available here, and its
 * mere presence doesn't prove it's still valid (it could be expired or
 * already revoked server-side). This only rules out the definitely-signed-
 * out case (no cookie at all). The definitive check — attempt a silent
 * refresh and react to the real outcome — happens client-side on /profile
 * via AuthProvider + the page's own effect.
 */
export function proxy(request: NextRequest) {
  if (!request.cookies.has(REFRESH_COOKIE_NAME)) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("from", request.nextUrl.pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

// /admin/:path* gets the same coarse "is there even a refresh cookie" gate
// as /profile and /bookings — nothing more is possible here. The refresh
// token cookie carries no role claim (only the in-memory access token JWT
// does — see JwtService.generateRefreshToken vs generateAccessToken on the
// backend), so Proxy cannot tell an ADMIN's cookie from a CUSTOMER's; it can
// only rule out "definitely signed out". The real ADMIN-role check —
// definitive, not a hidden-entry-point pseudo-guard — happens client-side in
// app/admin/layout.tsx via fetchCurrentUser(), the same "coarse gate here,
// definitive check after mount" split /profile already uses.
export const config = {
  matcher: ["/profile/:path*", "/bookings/:path*", "/admin/:path*"],
};
