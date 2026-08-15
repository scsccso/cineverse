"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { getMovie } from "@/lib/api/movies";
import { getGenres, updateMovie, uploadMoviePoster, uploadMovieBackdrop } from "@/lib/api/admin-movies";
import type { GenreResponse, MovieResponse } from "@/lib/api/types";
import { MovieForm } from "@/components/admin/movie-form";
import { MovieImageUpload } from "@/components/admin/movie-image-upload";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

export default function EditMoviePage() {
  const { id } = useParams<{ id: string }>();
  const { callAuthorized } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [movie, setMovie] = useState<MovieResponse | null>(null);
  const [genres, setGenres] = useState<GenreResponse[] | null>(null);
  const [loadError, setLoadError] = useState(false);
  // Read once via a lazy initializer rather than copied into state from an
  // effect body — react-hooks/set-state-in-effect (already hit once in this
  // codebase, see CLAUDE.md Phase 8 admin dashboard) flags synchronous
  // setState inside a useEffect as a cascading-render anti-pattern. The
  // param itself is still stripped from the URL below (same reasoning as
  // the seat picker's booking-resume flow, CLAUDE.md Phase 6: a refresh
  // shouldn't keep re-showing it) but that's a router side effect, not a
  // state update, so it doesn't trip the same rule.
  const [justCreated, setJustCreated] = useState(() => searchParams.get("created") === "1");
  // Set when the TMDB-prefilled create flow's follow-up "apply the TMDB
  // image URLs" call fails after the movie itself was already created
  // successfully (network blip, etc.) — must not fail silently, since the
  // poster/backdrop preview below would otherwise just show the placeholder
  // with no indication anything went wrong. Mutually exclusive with
  // justCreated in practice (see admin/movies/new/page.tsx): the TMDB path
  // sends this instead of ?created=1 when image setup fails, since "images
  // already applied, nothing to do below" and "images failed, do something
  // below" call for different messages, not the same generic one.
  const [imageSetupFailed, setImageSetupFailed] = useState(() => searchParams.get("imageSetupFailed") === "1");
  const [savedBanner, setSavedBanner] = useState(false);

  useEffect(() => {
    if (searchParams.get("created") === "1" || searchParams.get("imageSetupFailed") === "1") {
      router.replace(`/admin/movies/${id}/edit`);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getMovie(id), getGenres()])
      .then(([fetchedMovie, fetchedGenres]) => {
        if (cancelled) return;
        setMovie(fetchedMovie);
        setGenres(fetchedGenres);
      })
      .catch(() => {
        if (!cancelled) setLoadError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (loadError) {
    return (
      <section className="mx-auto max-w-2xl px-6 py-10">
        <p className="py-8 text-center text-sm text-destructive">Failed to load movie — it may have been deleted</p>
        <Link href="/admin/movies" className="mx-auto block w-fit text-sm text-muted-foreground hover:text-foreground">
          Back to Movies
        </Link>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-2xl px-6 py-10">
      <Link href="/admin/movies" className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden />
        Back to Movies
      </Link>

      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">Edit Movie{movie ? `: ${movie.title}` : ""}</h1>
      </header>

      {justCreated && (
        <div className="mb-6">
          <AnimatedFormBanner
            variant="success"
            message='Movie created — status defaults to "Coming Soon." You can upload the poster/backdrop below now, then switch it to "Now Playing" once everything checks out.'
          />
        </div>
      )}
      {imageSetupFailed && (
        <div className="mb-6">
          <AnimatedFormBanner
            variant="destructive"
            message="Movie created, but setting the poster/backdrop failed. Please upload them manually below."
          />
        </div>
      )}
      {savedBanner && (
        <div className="mb-6">
          <AnimatedFormBanner variant="success" message="Changes saved." />
        </div>
      )}

      {!movie || !genres ? (
        <p className="py-8 text-center text-sm text-muted-foreground">Loading...</p>
      ) : (
        <div className="space-y-6">
          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>Basic Info</CardTitle>
            </CardHeader>
            <CardContent>
              <MovieForm
                genres={genres}
                initialMovie={movie}
                onSave={(request) => callAuthorized((token) => updateMovie(token, id, request))}
                onSaved={(saved) => {
                  setMovie(saved);
                  setJustCreated(false);
                  setImageSetupFailed(false);
                  setSavedBanner(true);
                }}
                submitLabel="Save Changes"
                submittingLabel="Saving…"
              />
            </CardContent>
          </Card>

          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>Poster / Backdrop</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <MovieImageUpload
                label="Poster (portrait, used on listings/detail pages)"
                aspect="poster"
                currentUrl={movie.posterUrl}
                onUpload={(file) => callAuthorized((token) => uploadMoviePoster(token, id, file))}
                onUploaded={setMovie}
              />
              <Separator />
              <MovieImageUpload
                label="Backdrop (landscape, used on the homepage hero)"
                aspect="backdrop"
                currentUrl={movie.backdropUrl}
                onUpload={(file) => callAuthorized((token) => uploadMovieBackdrop(token, id, file))}
                onUploaded={setMovie}
              />
            </CardContent>
          </Card>
        </div>
      )}
    </section>
  );
}
