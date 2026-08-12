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
// every local-dev configuration this project has. Only relaxed when the
// configured backend origin is actually loopback, the same
// derive-from-environment-not-from-a-manual-flag approach the backend uses
// for `app.security.cookie-secure` (on in application.yml, off only in
// application-local.yml — see CLAUDE.md Phase 1) — a real deployment
// pointing NEXT_PUBLIC_API_BASE_URL at a public domain gets the restriction
// left on (its default, safest state) and keeps full image optimization,
// since a public domain was never going to resolve to a private IP in the
// first place.
const LOOPBACK_HOSTNAMES = new Set(["localhost", "127.0.0.1", "::1"]);
const backendIsLoopback = LOOPBACK_HOSTNAMES.has(apiBaseUrl.hostname);

const nextConfig: NextConfig = {
  images: {
    ...(backendIsLoopback ? { dangerouslyAllowLocalIP: true } : {}),
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
