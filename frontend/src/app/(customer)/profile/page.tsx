"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import type { UserResponse } from "@/lib/api/types";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { LogoutButton } from "@/components/auth/logout-button";
import { FadeIn } from "@/components/motion/fade-in";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

function initials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function formatJoinedDate(iso: string | null) {
  if (!iso) return "—";
  return new Intl.DateTimeFormat("en-GB", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(new Date(iso));
}

function ProfileSkeleton() {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center gap-4">
        <Skeleton className="size-16 rounded-full" />
        <div className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-4 w-56" />
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-4 w-24" />
      </CardContent>
    </Card>
  );
}

export default function ProfilePage() {
  const { status: authStatus, user, fetchCurrentUser } = useAuth();
  const router = useRouter();
  const [profile, setProfile] = useState<UserResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (authStatus === "loading") return;

    if (authStatus === "unauthenticated") {
      // Belt-and-suspenders: proxy.ts already gated on the refresh_token
      // cookie's presence server-side; this is the definitive client-side
      // check after the silent refresh attempt has resolved.
      router.replace("/login");
      return;
    }

    if (user?.role === "ADMIN") {
      // ADMIN reverse-gate: this page shows the logged-in customer's own
      // account details, which isn't a meaningful view for an ADMIN session.
      // Checked before fetchCurrentUser() fires below, so no request for
      // profile data (and no risk of it rendering) ever happens on this
      // branch — `profile` stays null and the loading skeleton below keeps
      // showing until the replace takes effect. See CLAUDE.md "Admin
      // reverse-isolation" for why this lives per-page instead of in
      // (customer)/layout.tsx.
      router.replace("/admin/dashboard");
      return;
    }

    let ignore = false;

    fetchCurrentUser()
      .then((user) => {
        if (ignore) return;
        setProfile(user);
        setLoadError(null);
      })
      .catch((error) => {
        if (ignore) return;
        if (error instanceof ApiError && error.status === 401) {
          router.replace("/login");
          return;
        }
        setLoadError("Failed to load your profile — please try again later");
      });

    return () => {
      ignore = true;
    };
  }, [authStatus, user, fetchCurrentUser, router]);

  if (authStatus === "loading" || (authStatus === "authenticated" && !profile && !loadError)) {
    return (
      <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-2xl flex-col justify-center px-6 py-16">
        <ProfileSkeleton />
      </section>
    );
  }

  if (loadError) {
    return (
      <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-2xl flex-col justify-center px-6 py-16 text-center text-muted-foreground">
        {loadError}
      </section>
    );
  }

  if (!profile) {
    return null;
  }

  return (
    <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-2xl flex-col justify-center px-6 py-16">
      <FadeIn>
        <Card>
          <CardHeader className="flex flex-row items-center gap-4">
            <div className="flex size-16 shrink-0 items-center justify-center rounded-full bg-primary text-xl font-semibold text-primary-foreground">
              {initials(profile.fullName)}
            </div>
            <div className="flex flex-1 flex-col gap-1">
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-semibold">{profile.fullName}</h1>
                <Badge variant={profile.role === "ADMIN" ? "default" : "secondary"}>
                  {profile.role}
                </Badge>
              </div>
              <p className="text-sm text-muted-foreground">{profile.email}</p>
            </div>
          </CardHeader>

          <CardContent>
            <Separator className="mb-4" />
            <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 text-sm">
              <dt className="text-muted-foreground">User ID</dt>
              <dd className="truncate font-mono text-xs">{profile.id}</dd>
              <dt className="text-muted-foreground">Joined</dt>
              <dd>{formatJoinedDate(profile.createdAt)}</dd>
            </dl>

            <div className="mt-8 flex items-center justify-between gap-4">
              <Link href="/bookings" className={cn(buttonVariants(), "h-11 px-6")}>
                My Bookings
              </Link>
              <LogoutButton />
            </div>
          </CardContent>
        </Card>
      </FadeIn>
    </section>
  );
}
