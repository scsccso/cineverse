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
    ],
  },
};

export default nextConfig;
