"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { createShowtime, listCinemas, listHalls } from "@/lib/api/admin-showtimes";
import { getAdminMovies } from "@/lib/api/admin-movies";
import type { HallResponse, MovieResponse } from "@/lib/api/types";
import { ShowtimeForm } from "@/components/admin/showtime-form";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

/** Scheduling a new showing for an already-ended movie doesn't make sense
 * for this project (no "re-release" scenario) — see CLAUDE.md's "Admin 场次
 * 管理" decision record. Filtered client-side rather than via a second
 * `GET /movies?status=` call per status: this MVP has 11 seed movies total,
 * so one unpaginated-enough fetch (size=100) is simpler than merging two
 * requests for a dropdown this small. */
const SCHEDULABLE_STATUSES = new Set(["NOW_PLAYING", "COMING_SOON"]);

export default function NewShowtimePage() {
  const { callAuthorized } = useAuth();
  const router = useRouter();
  const [movies, setMovies] = useState<MovieResponse[] | null>(null);
  const [halls, setHalls] = useState<HallResponse[] | null>(null);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      getAdminMovies(0, 100),
      listCinemas().then((cinemas) => (cinemas.length > 0 ? listHalls(cinemas[0].id) : [])),
    ])
      .then(([moviePage, hallList]) => {
        if (cancelled) return;
        setMovies(moviePage.content.filter((movie) => SCHEDULABLE_STATUSES.has(movie.status)));
        setHalls(hallList);
      })
      .catch(() => {
        if (!cancelled) setLoadError(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section className="mx-auto max-w-2xl px-6 py-10">
      <Link
        href="/admin/showtimes"
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Back to Showtimes
      </Link>

      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">Add Showtime</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          No editing — if a showtime is entered wrong, delete it and create a new one, matching the backend API&apos;s design.
        </p>
      </header>

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>Showtime Info</CardTitle>
        </CardHeader>
        <CardContent>
          {loadError ? (
            <p className="py-8 text-center text-sm text-destructive">Failed to load movies/halls. Please refresh and try again.</p>
          ) : !movies || !halls ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Loading...</p>
          ) : (
            <ShowtimeForm
              movies={movies}
              halls={halls}
              onSave={(request) => callAuthorized((token) => createShowtime(token, request))}
              onSaved={() => router.push("/admin/showtimes")}
            />
          )}
        </CardContent>
      </Card>
    </section>
  );
}
