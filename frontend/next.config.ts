import type { NextConfig } from "next";

// Posters/backdrops are served by the Spring Boot backend, not this app, so
// next/image needs an explicit allowlist. Derived from the same env var the
// API client uses (see src/lib/api/client.ts) instead of a hardcoded host,
// so it tracks whatever backend origin the deployment actually points at.
// 8081, not 8080 — see lib/api/client.ts's API_BASE_URL comment and
// docs/DEVELOPMENT.md's port-conflict note (this fallback only matters if
// NEXT_PUBLIC_API_BASE_URL is unset, e.g. a fresh worktree missing
// .env.local, same trap that bit lib/api/client.ts/admin-reports.ts).
const apiBaseUrl = new URL(
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081",
);

// Next.js 16 added a default-on SSRF guard that refuses to optimize images
// whose upstream host resolves to a private/loopback IP (see "Local IP
// Restriction" in node_modules/next/dist/docs/01-app/02-guides/upgrading/
// version-16.md and the images.dangerouslyAllowLocalIP entry in
// node_modules/next/dist/docs/01-app/03-api-reference/02-components/
// image.md) — every locally-uploaded poster/backdrop 404s through
// next/image as a result, since the backend origin above is "localhost" in
// every local-dev configuration this project has.
//
// Deliberately NOT inferred from apiBaseUrl.hostname (an earlier version of
// this file did exactly that, comparing it against a literal
// localhost/127.0.0.1/::1 set) — that has two real failure modes caught in
// pre-merge review, not hypothetical ones:
//   1. A same-host reverse-proxy production deployment can legitimately set
//      NEXT_PUBLIC_API_BASE_URL to a literal "localhost" URL too (frontend
//      and backend co-located, proxied internally) — a hostname string
//      match can't tell that apart from actual local dev, and would relax
//      an SSRF guard in production based on nothing more than a coincidence
//      of URL spelling.
//   2. If NEXT_PUBLIC_API_BASE_URL is unset, the fallback above resolves to
//      "http://localhost:8081" — whose hostname IS "localhost" — so the
//      inferred flag would default to true on a missing/misconfigured env
//      var. CI's frontend job (.github/workflows/ci.yml) runs `npm run
//      build` without setting this var at all, so that failure mode isn't
//      hypothetical either: it's exactly what CI's own build would hit.
//
// A dedicated opt-in env var has neither problem: it is never "true" unless
// someone deliberately sets it to the literal string "true" for a build
// they know talks to a local backend, and every other case (unset, any
// other value, CI, a real deployment) gets the safe default. This is the
// same "which environment is this" signal the backend's
// app.security.cookie-secure split relies on (SPRING_PROFILES_ACTIVE is a
// dedicated environment declaration, not inferred from the datasource URL —
// see CLAUDE.md Phase 1) rather than overloading an unrelated value (what
// URL to fetch) to also answer a security-posture question it was never
// designed to answer.
const LOOPBACK_HOSTNAMES = new Set(["localhost", "127.0.0.1", "::1"]);
const backendIsLoopback = LOOPBACK_HOSTNAMES.has(apiBaseUrl.hostname);
const localImageOptimizationOptIn =
  process.env.NEXT_ALLOW_LOCAL_IMAGE_OPTIMIZATION === "true";
// Both conditions, not just the opt-in alone — dangerouslyAllowLocalIP only
// ever changes behavior when the origin really is loopback in the first
// place, so requiring both keeps the intent ("only for private networks",
// straight from the Next.js docs above) self-documenting in the code, not
// just in this comment.
const allowLocalImageOptimization = backendIsLoopback && localImageOptimizationOptIn;

const nextConfig: NextConfig = {
  images: {
    ...(allowLocalImageOptimization ? { dangerouslyAllowLocalIP: true } : {}),
    remotePatterns: [
      {
        protocol: apiBaseUrl.protocol.replace(":", "") as "http" | "https",
        hostname: apiBaseUrl.hostname,
        port: apiBaseUrl.port,
        pathname: "/**",
      },
      // OMDb API poster images (see docs/DEVELOPMENT.md's seed-data section)
      // are hotlinked straight from Amazon's CDN rather than downloaded and
      // re-hosted through StorageService — this is the host every OMDb
      // "Poster" field resolves to, not specific to any one movie.
      {
        protocol: "https",
        hostname: "m.media-amazon.com",
        pathname: "/**",
      },
      // TMDB backdrop images (see CLAUDE.md "backdrop 和 poster 分别用两个
      // 不同数据源") — every image.tmdb.org/t/p/{size}/ URL resolves through
      // this one host regardless of size prefix or movie.
      {
        protocol: "https",
        hostname: "image.tmdb.org",
        pathname: "/**",
      },
    ],
  },
};

export default nextConfig;
