"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { getGenres, createMovie, setMovieImageUrls } from "@/lib/api/admin-movies";
import type { GenreResponse, MovieResponse, TmdbMovieDetail } from "@/lib/api/types";
import { MovieForm } from "@/components/admin/movie-form";
import { TmdbSearchPicker } from "@/components/admin/tmdb-search-picker";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

type Mode = "tmdb" | "manual";

export default function NewMoviePage() {
  const { callAuthorized } = useAuth();
  const router = useRouter();
  const [genres, setGenres] = useState<GenreResponse[] | null>(null);
  const [genresError, setGenresError] = useState(false);
  // TMDB search is the default path, not manual entry — see CLAUDE.md's
  // "why TMDB search is the primary path" note. Manual stays one click away
  // via the fallback link below, and switching to it never destroys
  // anything: nothing has been submitted yet at this point either way.
  const [mode, setMode] = useState<Mode>("tmdb");
  // Set once a TMDB result's full detail has been fetched — from then on
  // the create form renders instead of the picker, prefilled from this.
  // Image URLs live here (not in MovieForm/MovieRequest — see
  // TmdbMovieDetail's doc comment) and are applied via a separate PATCH
  // call after the movie itself is created, see handleSaved below.
  const [tmdbPrefill, setTmdbPrefill] = useState<TmdbMovieDetail | null>(null);

  useEffect(() => {
    getGenres()
      .then(setGenres)
      .catch(() => setGenresError(true));
  }, []);

  async function handleSaved(saved: MovieResponse) {
    const hasImages = tmdbPrefill && (tmdbPrefill.posterUrl || tmdbPrefill.backdropUrl);
    if (!hasImages) {
      // Manual creation, or a TMDB pick that happened to have no images —
      // same "now go upload something" guidance either way.
      router.push(`/admin/movies/${saved.id}/edit?created=1`);
      return;
    }
    try {
      await callAuthorized((token) =>
        setMovieImageUrls(token, saved.id, {
          posterUrl: tmdbPrefill.posterUrl ?? undefined,
          backdropUrl: tmdbPrefill.backdropUrl ?? undefined,
        }),
      );
      // No "created" banner here on purpose — the poster/backdrop cards on
      // the edit page will already show the real TMDB images, not
      // placeholders, which is itself the confirmation; repeating "you can
      // upload images below" would be stale guidance for a state that's
      // already handled.
      router.push(`/admin/movies/${saved.id}/edit`);
    } catch {
      // The movie itself was created successfully — this failure is only
      // the follow-up image step, and it must not fail silently (the admin
      // would otherwise see placeholder images with no explanation). See
      // the edit page's imageSetupFailed banner.
      router.push(`/admin/movies/${saved.id}/edit?imageSetupFailed=1`);
    }
  }

  return (
    <section className="mx-auto max-w-2xl px-6 py-10">
      <Link href="/admin/movies" className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden />
        Back to Movies
      </Link>

      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">Add Movie</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {mode === "tmdb" && !tmdbPrefill
            ? "Search TMDB and pick a matching entry to auto-fill the title/description/duration/trailer/poster/backdrop — content rating, user rating, status, and genres still need to be filled in manually."
            : "Posters and backdrops need to be uploaded separately after the movie is created — submitting will take you to the movie's edit page to upload images there."}
        </p>
      </header>

      <Card className="shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between gap-3">
          <CardTitle>{mode === "tmdb" && !tmdbPrefill ? "Search TMDB" : "Basic Info"}</CardTitle>
          {mode === "tmdb" && tmdbPrefill && (
            <Button type="button" variant="ghost" size="sm" onClick={() => setTmdbPrefill(null)}>
              Search Again
            </Button>
          )}
        </CardHeader>
        <CardContent>
          {genresError ? (
            <p className="py-8 text-center text-sm text-destructive">Failed to load genres. Please refresh and try again.</p>
          ) : !genres ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Loading...</p>
          ) : mode === "tmdb" && !tmdbPrefill ? (
            <div className="space-y-4">
              <TmdbSearchPicker onSelect={setTmdbPrefill} />
              <div className="border-t border-border pt-4 text-center">
                <Button type="button" variant="ghost" size="sm" onClick={() => setMode("manual")}>
                  Can&apos;t find it? Create manually
                </Button>
              </div>
            </div>
          ) : (
            <MovieForm
              genres={genres}
              tmdbPrefill={tmdbPrefill ?? undefined}
              onSave={(request) => callAuthorized((token) => createMovie(token, request))}
              onSaved={handleSaved}
              submitLabel="Create Movie"
              submittingLabel="Creating…"
            />
          )}
        </CardContent>
      </Card>
    </section>
  );
}
