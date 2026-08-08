import type { NextConfig } from "next";

// Posters/backdrops are served by the Spring Boot backend, not this app, so
// next/image needs an explicit allowlist. Derived from the same env var the
// API client uses (see src/lib/api/client.ts) instead of a hardcoded host,
// so it tracks whatever backend origin the deployment actually points at.
const apiBaseUrl = new URL(
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
);

const nextConfig: NextConfig = {
  images: {
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
