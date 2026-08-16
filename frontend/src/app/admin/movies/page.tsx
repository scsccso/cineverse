"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { Plus, Search } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { getAdminMovies, deleteMovie } from "@/lib/api/admin-movies";
import { resolveMediaUrl, ApiError } from "@/lib/api/client";
import type { MovieResponse, MovieStatus, Page } from "@/lib/api/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

const STATUS_LABELS: Record<MovieStatus, string> = {
  NOW_PLAYING: "Now Playing",
  COMING_SOON: "Coming Soon",
  ENDED: "Ended",
};

const STATUS_BADGE_VARIANT: Record<MovieStatus, "default" | "secondary" | "outline"> = {
  NOW_PLAYING: "default",
  COMING_SOON: "secondary",
  ENDED: "outline",
};

export default function AdminMoviesPage() {
  const { callAuthorized } = useAuth();
  const [titleInput, setTitleInput] = useState("");
  const [appliedTitle, setAppliedTitle] = useState("");
  const [page, setPage] = useState(0);

  // Same derived-loading pattern as admin/users/page.tsx, extended to also
  // track the search term that produced the data on hand — a title change
  // is a second, independent axis a plain "moviesPage.number !== page"
  // check can't see, so the held result now carries the params it was
  // actually fetched for (same shape as admin/bookings/admin/payments).
  const [result, setResult] = useState<{ page: number; title: string; data: Page<MovieResponse> } | null>(null);
  const loading = !result || result.page !== page || result.title !== appliedTitle;

  const [error, setError] = useState<string | null>(null);

  const [confirmDialog, setConfirmDialog] = useState<{
    isOpen: boolean;
    title: string;
    description: string;
    action: () => Promise<void>;
  }>({ isOpen: false, title: "", description: "", action: async () => {} });
  const [actionError, setActionError] = useState<string | null>(null);

  // Same stale-response guard as admin/users/page.tsx — GET /api/v1/movies
  // is public so this doesn't need callAuthorized, but a fast page-flip can
  // still land responses out of order.
  const requestIdRef = useRef(0);

  useEffect(() => {
    const thisRequestId = ++requestIdRef.current;
    getAdminMovies(page, 20, appliedTitle || undefined)
      .then((data) => {
        if (requestIdRef.current !== thisRequestId) return;
        setResult({ page, title: appliedTitle, data });
        setError(null);
      })
      .catch(() => {
        if (requestIdRef.current !== thisRequestId) return;
        setError("Failed to load movies");
      });
  }, [page, appliedTitle]);

  // Explicit submit, not live-as-you-type — same convention as the TMDB
  // search picker and admin/bookings' search form (see CLAUDE.md): one
  // request per deliberate search action, not one per keystroke.
  function handleSearchSubmit(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedTitle(titleInput.trim());
  }

  const handleDelete = (movie: MovieResponse) => {
    setConfirmDialog({
      isOpen: true,
      title: "Delete Movie",
      description: `Are you sure you want to delete "${movie.title}"? This action cannot be undone.`,
      action: async () => {
        try {
          await callAuthorized((token) => deleteMovie(token, movie.id));
          setResult((prev) =>
            prev ? { ...prev, data: { ...prev.data, content: prev.data.content.filter((m) => m.id !== movie.id) } } : prev,
          );
          closeDialog();
        } catch (err: unknown) {
          // Shown as-is — e.g. the 409 "still has scheduled showtimes"
          // message from MovieHasScheduledShowtimesException is already
          // written to be read directly, no need to re-word it here.
          setActionError(err instanceof ApiError ? err.message : "Failed to delete. Please try again later.");
        }
      },
    });
  };

  const closeDialog = () => {
    setConfirmDialog((prev) => ({ ...prev, isOpen: false }));
    setActionError(null);
  };

  return (
    <section className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-semibold text-foreground">Admin · Movies</h1>
          <p className="mt-1 text-sm text-muted-foreground">Manage movie info, status, and poster/backdrop images</p>
        </div>
        <Link href="/admin/movies/new" className="inline-flex">
          <Button type="button" className="h-11">
            <Plus className="size-4" aria-hidden />
            Add Movie
          </Button>
        </Link>
      </header>

      <form onSubmit={handleSearchSubmit} className="mb-6 flex max-w-sm items-end gap-2">
        <Field className="flex-1">
          <FieldLabel htmlFor="movieTitleSearch">Search by title</FieldLabel>
          <Input
            id="movieTitleSearch"
            placeholder="Interstellar"
            value={titleInput}
            onChange={(event) => setTitleInput(event.target.value)}
          />
        </Field>
        <Button type="submit" variant="outline" className="h-11">
          <Search className="size-4" aria-hidden />
          Search
        </Button>
      </form>

      {error && (
        <div role="alert" className="mb-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>Movies</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-8 text-center text-sm text-muted-foreground">Loading...</div>
          ) : result.data.content.length === 0 ? (
            <div className="py-8 text-center text-sm text-muted-foreground">
              {appliedTitle ? `No movies match "${appliedTitle}"` : "No movies yet"}
            </div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <caption className="sr-only">Movies</caption>
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                    <th scope="col" className="px-3 py-2 font-medium">Poster</th>
                    <th scope="col" className="px-3 py-2 font-medium">Title</th>
                    <th scope="col" className="px-3 py-2 font-medium">Status</th>
                    <th scope="col" className="px-3 py-2 font-medium">Genres</th>
                    <th scope="col" className="px-3 py-2 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((movie) => (
                    <tr key={movie.id} className="border-b border-border last:border-0">
                      <td className="px-3 py-2">
                        <div className="relative aspect-[2/3] w-12 overflow-hidden rounded border border-border bg-muted">
                          <Image src={resolveMediaUrl(movie.posterUrl)} alt="" fill sizes="48px" className="object-cover" />
                        </div>
                      </td>
                      <td className="px-3 py-2 text-foreground">{movie.title}</td>
                      <td className="px-3 py-2">
                        <Badge variant={STATUS_BADGE_VARIANT[movie.status]}>{STATUS_LABELS[movie.status]}</Badge>
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex flex-wrap gap-1">
                          {movie.genres.length === 0 ? (
                            <span className="text-muted-foreground">—</span>
                          ) : (
                            movie.genres.map((genre) => (
                              <Badge key={genre.id} variant="outline">
                                {genre.name}
                              </Badge>
                            ))
                          )}
                        </div>
                      </td>
                      <td className="px-3 py-2 text-right">
                        <div className="flex justify-end gap-2">
                          <Link href={`/admin/movies/${movie.id}/edit`}>
                            <Button type="button" variant="outline" size="sm">
                              Edit
                            </Button>
                          </Link>
                          <Button type="button" variant="destructive" size="sm" onClick={() => handleDelete(movie)}>
                            Delete
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {result && result.data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between">
              <p className="text-sm text-muted-foreground">
                {result.data.totalElements} total, page {result.data.number + 1} of {result.data.totalPages}
              </p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={result.data.first || loading}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={result.data.last || loading}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {confirmDialog.isOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-dialog-title"
        >
          <Card className="w-full max-w-md shadow-lg">
            <CardHeader>
              <CardTitle id="confirm-dialog-title">{confirmDialog.title}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">{confirmDialog.description}</p>

              {actionError && (
                <div role="alert" className="mt-4 rounded border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                  {actionError}
                </div>
              )}

              <div className="mt-6 flex justify-end gap-3">
                <Button type="button" variant="outline" onClick={closeDialog}>
                  Cancel
                </Button>
                <Button type="button" variant="default" onClick={confirmDialog.action}>
                  Confirm
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </section>
  );
}
