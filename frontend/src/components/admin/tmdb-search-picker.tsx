"use client";

import { useRef, useState } from "react";
import Image from "next/image";
import { Film, Loader2, Search } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { searchTmdbMovies, getTmdbMovieDetail } from "@/lib/api/admin-movies";
import { ApiError } from "@/lib/api/client";
import type { TmdbMovieDetail, TmdbSearchResult } from "@/lib/api/types";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";

interface TmdbSearchPickerProps {
  /** Fires once a result is picked AND its full detail has been fetched —
   * not on the initial (slim) search result click itself. */
  onSelect: (detail: TmdbMovieDetail) => void;
}

/** Search box + results list for prefilling the create-movie form from
 * TMDB. Two TMDB calls total across a full pick: one for the search
 * (Enter/button-triggered, not live-as-you-type — see CLAUDE.md's TMDB
 * call-budget note), one for the chosen result's detail, fired exactly once
 * on selection. */
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
          ? "TMDB 搜索暂时不可用,请使用下方的手动创建"
          : "搜索失败,请重试",
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
          ? "获取详情失败,TMDB 暂时不可用,请使用下方的手动创建"
          : "获取详情失败,请重试",
      );
    } finally {
      setSelectingId(null);
    }
  }

  return (
    <div className="space-y-4">
      <form onSubmit={handleSearch} className="flex gap-2">
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="输入片名搜索 TMDB…"
          aria-label="TMDB 片名搜索"
        />
        <Button type="submit" disabled={searching || !query.trim()} className="shrink-0">
          {searching ? <Loader2 className="size-4 animate-spin motion-reduce:animate-none" aria-hidden /> : <Search className="size-4" aria-hidden />}
          {searching ? "搜索中…" : "搜索"}
        </Button>
      </form>
      <AnimatedFieldError message={searchError ?? undefined} />

      {results && results.length === 0 && !searchError && (
        <p className="py-4 text-center text-sm text-muted-foreground">未找到匹配结果,可以尝试其他关键词,或使用下方的手动创建</p>
      )}

      {results && results.length > 0 && (
        <ul className="max-h-96 space-y-2 overflow-y-auto" aria-label="TMDB 搜索结果">
          {results.map((result) => (
            <li key={result.tmdbId}>
              <button
                type="button"
                onClick={() => handleSelect(result)}
                disabled={selectingId !== null}
                aria-busy={selectingId === result.tmdbId}
                className="flex w-full items-center gap-3 rounded-lg border border-border bg-background p-2 text-left transition-colors hover:bg-muted disabled:pointer-events-none disabled:opacity-50"
              >
                <div className="relative aspect-[2/3] w-12 shrink-0 overflow-hidden rounded bg-muted">
                  {result.posterUrl ? (
                    <Image src={result.posterUrl} alt="" fill sizes="48px" className="object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center">
                      <Film className="size-4 text-muted-foreground" aria-hidden />
                    </div>
                  )}
                </div>
                <span className="flex-1 text-sm text-foreground">
                  {result.title}
                  {result.releaseYear && <span className="text-muted-foreground"> ({result.releaseYear})</span>}
                </span>
                {selectingId === result.tmdbId && (
                  <Loader2 className="size-4 shrink-0 animate-spin text-muted-foreground motion-reduce:animate-none" aria-hidden />
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
      <AnimatedFieldError message={selectError ?? undefined} />
    </div>
  );
}
