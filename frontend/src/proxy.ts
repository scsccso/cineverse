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

export const config = {
  matcher: ["/profile/:path*"],
};
