import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import localFont from "next/font/local";
import "./globals.css";
import { AuthProvider } from "@/lib/auth/auth-context";

const inter = Inter({
  variable: "--font-sans",
  subsets: ["latin"],
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  display: "swap",
});

// Clash Display isn't on next/font/google — Fontshare doesn't publish an npm
// package either, but its CDN (api.fontshare.com/v2/css) serves plain woff2
// files under the same free license as the CDN <link>, so they're
// downloaded once into src/app/fonts/ and self-hosted via next/font/local
// instead of fetched at runtime from a <link> tag. Gets the same
// subsetting/no-FOIT-FOUT optimization next/font/google gives Inter/JetBrains
// Mono above, and sidesteps the hydration error a raw <link> under <html>
// caused (browsers silently reparent invalid <html> children before React
// hydrates, so server/client trees stop matching).
const clashDisplay = localFont({
  src: [
    { path: "./fonts/ClashDisplay-Medium.woff2", weight: "500", style: "normal" },
    { path: "./fonts/ClashDisplay-Semibold.woff2", weight: "600", style: "normal" },
    { path: "./fonts/ClashDisplay-Bold.woff2", weight: "700", style: "normal" },
  ],
  variable: "--font-display",
  display: "swap",
});

export const metadata: Metadata = {
  title: "CineVerse",
  description: "Online cinema seat booking system",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="zh-CN"
      data-scroll-behavior="smooth"
      className={`dark ${inter.variable} ${jetbrainsMono.variable} ${clashDisplay.variable} h-full antialiased`}
    >
      <body className="min-h-full bg-background text-foreground">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
