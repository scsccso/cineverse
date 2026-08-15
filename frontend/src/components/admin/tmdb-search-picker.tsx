"use client";

import { useRef, useState } from "react";
import Image from "next/image";
import { Check, Film, Loader2, Search, SearchX } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { searchTmdbMovies, getTmdbMovieDetail } from "@/lib/api/admin-movies";
import { ApiError } from "@/lib/api/client";
import type { TmdbMovieDetail, TmdbSearchResult } from "@/lib/api/types";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";

interface TmdbSearchPickerProps {
  /** Fires once a result is picked AND its full detail has been fetched —
   * not on the initial (slim) search result click itself. */
  onSelect: (detail: TmdbMovieDetail) => void;
}

const SKELETON_COUNT = 6;

/** Poster-forward result grid, not a text list: picking a movie here is a
 * visual recognition task ("which one is the right Dune?"), so the poster —
 * not the title string — is the thing the admin actually scans. Column count
 * drops with the *container* width (@container queries, not viewport
 * breakpoints) because this renders inside a fixed max-w-2xl card, so
 * viewport width alone wouldn't describe the space actually available.
 *
 * Card visuals reuse the admin Card component's own tokens (rounded-xl /
 * bg-card / ring-1 ring-foreground/10 / shadow-sm) applied directly to the
 * button rather than nesting an actual <Card>: Card's padding model assumes
 * padded content, and these are full-bleed poster tiles. Same
 * "reuse the visual language, not the component" precedent as the seat map
 * (CLAUDE.md 1.5). Deliberately NOT GlassCard — /admin/** is the light,
 * flat-surface exception with no pointer-tracking glow (CLAUDE.md 1.5.1).
 */
export function TmdbSearchPicker({ onSelect }: TmdbSearchPickerProps) {
  const { callAuthorized } = useAuth();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<TmdbSearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [selectingId, setSelectingId] = useState<number | null>(null);
  const [selectError, setSelectError] = useState<string | null>(null);
  // Guards against an earlier, slower search response landing after a
  // later, faster one — same request-id pattern used throughout the admin
  // pages (see CLAUDE.md "Admin 用户管理" — loading derived from data state
  // + request-id to discard stale responses), reused here because a search
  // box invites exactly the "fire another search before the first replies"
  // scenario that pattern exists for.
  const requestIdRef = useRef(0);

  async function handleSearch(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;

    const thisRequestId = ++requestIdRef.current;
    setSearching(true);
    setSearchError(null);
    try {
      const found = await callAuthorized((token) => searchTmdbMovies(token, trimmed));
      if (requestIdRef.current !== thisRequestId) return;
      setResults(found);
    } catch (error) {
      if (requestIdRef.current !== thisRequestId) return;
      setResults(null);
      // Backend message is English (matches GlobalExceptionHandler's other
      // handlers, see the handler's own comment) — translated here rather
      // than shown as-is, unlike the CRUD 409s elsewhere in this feature.
      setSearchError(
        error instanceof ApiError && error.status === 502
          ? "TMDB search is temporarily unavailable — use manual creation below"
          : "Search failed. Please try again.",
      );
    } finally {
      if (requestIdRef.current === thisRequestId) setSearching(false);
    }
  }

  async function handleSelect(result: TmdbSearchResult) {
    setSelectingId(result.tmdbId);
    setSelectError(null);
    try {
      const detail = await callAuthorized((token) => getTmdbMovieDetail(token, result.tmdbId));
      onSelect(detail);
    } catch (error) {
      setSelectError(
        error instanceof ApiError && error.status === 502
          ? "Failed to fetch details — TMDB is temporarily unavailable, use manual creation below"
          : "Failed to fetch details. Please try again.",
      );
    } finally {
      setSelectingId(null);
    }
  }

  return (
    <div className="@container space-y-4">
      <form onSubmit={handleSearch} className="flex gap-2">
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Enter a title to search TMDB…"
          aria-label="TMDB title search"
        />
        <Button type="submit" disabled={searching || !query.trim()} className="shrink-0">
          {searching ? <Loader2 className="size-4 animate-spin motion-reduce:animate-none" aria-hidden /> : <Search className="size-4" aria-hidden />}
          {searching ? "Searching…" : "Search"}
        </Button>
      </form>
      <AnimatedFieldError message={searchError ?? undefined} />

      {/* Skeletons replace any previous results while a new search is in
          flight — showing the *old* result set under a spinning button read
          as "these are your results" when they were the previous query's. */}
      {searching ? (
        <div className="grid grid-cols-2 gap-3 @md:grid-cols-3 @xl:grid-cols-4" aria-hidden>
          {Array.from({ length: SKELETON_COUNT }).map((_, index) => (
            <div key={index} className="overflow-hidden rounded-xl ring-1 ring-foreground/10">
              <Skeleton className="aspect-[2/3] w-full rounded-none motion-reduce:animate-none" />
              <div className="space-y-1.5 p-2">
                <Skeleton className="h-4 w-4/5 motion-reduce:animate-none" />
                <Skeleton className="h-3 w-10 motion-reduce:animate-none" />
              </div>
            </div>
          ))}
        </div>
      ) : results && results.length === 0 && !searchError ? (
        <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
          <SearchX className="size-8 text-muted-foreground" aria-hidden />
          <p className="text-sm font-medium text-foreground">No matching movies found</p>
          <p className="max-w-sm text-sm text-muted-foreground">
            Double-check the spelling, or try a different keyword (the original English title often works better). You can also enter it manually below.
          </p>
        </div>
      ) : results && results.length > 0 ? (
        /* Capped + scrollable rather than growing unbounded, so the
           "Create manually" fallback underneath stays reachable without scrolling
           past 20 results. 34rem fits two full rows at the default admin
           width; a partially-visible row at narrower widths is the intended
           "there's more below" affordance, not a clipping bug. */
        <ul
          className="grid max-h-[34rem] grid-cols-2 gap-3 overflow-y-auto p-0.5 @md:grid-cols-3 @xl:grid-cols-4"
          aria-label="TMDB search results"
        >
          {results.map((result) => {
            const isSelecting = selectingId === result.tmdbId;
            const otherSelecting = selectingId !== null && !isSelecting;
            return (
              <li key={result.tmdbId}>
                <button
                  type="button"
                  onClick={() => handleSelect(result)}
                  disabled={selectingId !== null}
                  aria-busy={isSelecting}
                  className={`flex w-full flex-col overflow-hidden rounded-xl bg-card text-left shadow-sm transition-[box-shadow,opacity] outline-none ring-1 focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none motion-reduce:transition-none ${
                    isSelecting
                      ? "ring-2 ring-primary"
                      : "ring-foreground/10 hover:shadow-md hover:ring-foreground/30"
                  } ${otherSelecting ? "opacity-40" : ""}`}
                >
                  <div className="relative aspect-[2/3] w-full bg-muted">
                    {result.posterUrl ? (
                      <Image
                        src={result.posterUrl}
                        alt=""
                        fill
                        sizes="(max-width: 640px) 45vw, 200px"
                        className="object-cover"
                      />
                    ) : (
                      <div className="flex h-full w-full flex-col items-center justify-center gap-1 text-muted-foreground">
                        <Film className="size-6" aria-hidden />
                        <span className="text-xs">No poster</span>
                      </div>
                    )}
                    {/* Immediate "you hit this one" confirmation — the detail
                        fetch that follows takes long enough that a click with
                        no feedback felt like a no-op before the page swapped
                        to the prefilled form. Two icons, two different
                        meanings: the check is "this is the one you picked"
                        (already true), the spinner is "the detail request is
                        still running" (not done yet) — the check alone read as
                        "finished" while the app was still working. Same
                        animate-spin + motion-reduce pairing as the search
                        button above, not a bespoke treatment. */}
                    {isSelecting && (
                      <div className="absolute inset-0 flex items-center justify-center bg-primary/25">
                        <span className="flex items-center gap-1.5 rounded-full bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground">
                          <Check className="size-3.5" aria-hidden />
                          Selected
                          <Loader2 className="size-3.5 animate-spin motion-reduce:animate-none" aria-hidden />
                        </span>
                      </div>
                    )}
                  </div>
                  <div className="space-y-0.5 p-2">
                    <p className="line-clamp-2 text-sm leading-snug font-medium text-foreground">{result.title}</p>
                    <p className="font-mono text-xs text-muted-foreground">{result.releaseYear ?? "Year unknown"}</p>
                  </div>
                </button>
              </li>
            );
          })}
        </ul>
      ) : null}
      <AnimatedFieldError message={selectError ?? undefined} />
    </div>
  );
}
