"use client";

import Link from "next/link";
import { Clapperboard } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { LogoutButton } from "@/components/auth/logout-button";
import { cn } from "@/lib/utils";

const placeholderLinks = ["正在热映", "即将上映", "影院"];

export function Navbar() {
  const { status, user } = useAuth();

  return (
    <header className="sticky top-0 z-50 border-b border-border/60 bg-background/70 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
        <Link
          href="/"
          className="flex items-center gap-2 text-lg font-semibold tracking-tight"
        >
          <Clapperboard className="size-5 text-primary" aria-hidden />
          Cine<span className="text-primary">Verse</span>
        </Link>

        <nav className="hidden items-center gap-8 md:flex">
          {placeholderLinks.map((label) => (
            <span
              key={label}
              aria-disabled
              title="即将推出"
              className="cursor-not-allowed text-sm text-muted-foreground/50"
            >
              {label}
            </span>
          ))}
        </nav>

        <div className="flex items-center gap-3">
          {status === "loading" && (
            <>
              <Skeleton className="h-8 w-16 rounded-lg" />
              <Skeleton className="h-8 w-20 rounded-lg" />
            </>
          )}

          {status === "unauthenticated" && (
            <>
              <Link
                href="/login"
                className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}
              >
                登录
              </Link>
              <Link
                href="/register"
                className={cn(buttonVariants({ variant: "default", size: "sm" }))}
              >
                注册
              </Link>
            </>
          )}

          {status === "authenticated" && (
            <>
              <Link
                href="/profile"
                className="hidden text-sm text-muted-foreground hover:text-foreground sm:inline"
              >
                {user?.fullName ?? "我的账户"}
              </Link>
              <LogoutButton />
            </>
          )}
        </div>
      </div>
    </header>
  );
}
