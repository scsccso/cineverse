import type { MovieResponse, MovieStatusHistoryEntry } from "@/lib/api/types";
import { MOVIE_STATUS_BADGE_VARIANT, MOVIE_STATUS_LABELS } from "@/lib/movie-status";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

interface MovieStatusHistoryPanelProps {
  movie: MovieResponse;
  /** null covers both "still loading" and "the fetch failed" — same
   * convention as the edit page's upcomingShowtimeCount: a secondary
   * signal for a section that's collapsed by default, not the core edit
   * flow, so a failure here stays silent rather than raising an error
   * banner. Empty array is a distinct, real state: a movie created before
   * this feature shipped, with no recorded history — see CLAUDE.md's
   * "no backfill" decision (a fabricated first row would misrepresent
   * exactly when this movie actually reached its current status). */
  entries: MovieStatusHistoryEntry[] | null;
}

/** Derived client-side from the raw timeline, not returned by the backend
 * — pure arithmetic on already-fetched timestamps with no business rule
 * that could drift between front/backend (unlike e.g. seat columnSpan,
 * which does encode one). Entries are newest-first, so entries[0] is
 * always the start of the movie's current status stretch. */
function daysInCurrentStatus(entries: MovieStatusHistoryEntry[]): number {
  const since = new Date(entries[0].changedAt).getTime();
  return Math.max(0, Math.floor((Date.now() - since) / (1000 * 60 * 60 * 24)));
}

function timesEnteredNowPlaying(entries: MovieStatusHistoryEntry[]): number {
  return entries.filter((entry) => entry.toStatus === "NOW_PLAYING").length;
}

function pluralDays(count: number): string {
  return `${count} day${count === 1 ? "" : "s"}`;
}

function pluralTimes(count: number): string {
  return `${count} time${count === 1 ? "" : "s"}`;
}

/** Collapsed by default, native <details> — same accessible-collapse
 * mechanism the Phase 8 report cards already use for their data tables, not
 * a new custom widget. Placed at the very bottom of the edit page: this is
 * reference/lookup information, not something that needs to compete with
 * Basic Info, the image cards, or ScheduleShowtimesNudge for attention. */
export function MovieStatusHistoryPanel({ movie, entries }: MovieStatusHistoryPanelProps) {
  return (
    <Card className="shadow-sm">
      <CardContent>
        <details>
          <summary className="cursor-pointer text-sm font-medium text-foreground select-none">Status History</summary>
          <div className="mt-4">
            {!entries ? (
              <p className="text-sm text-muted-foreground">Loading…</p>
            ) : entries.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No history recorded before this movie&apos;s creation on{" "}
                {new Date(movie.createdAt).toLocaleDateString("en-GB")} — this feature shipped after this movie was
                added.
              </p>
            ) : (
              <div className="space-y-4">
                <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div className="rounded-lg border border-border bg-background p-3">
                    <dt className="text-xs text-muted-foreground">Currently {MOVIE_STATUS_LABELS[movie.status]} for</dt>
                    <dd className="font-mono text-lg font-semibold text-foreground">
                      {pluralDays(daysInCurrentStatus(entries))}
                    </dd>
                  </div>
                  <div className="rounded-lg border border-border bg-background p-3">
                    <dt className="text-xs text-muted-foreground">Entered Now Playing</dt>
                    <dd className="font-mono text-lg font-semibold text-foreground">
                      {pluralTimes(timesEnteredNowPlaying(entries))}
                    </dd>
                  </div>
                </dl>
                <ul className="space-y-2">
                  {entries.map((entry) => (
                    <li key={entry.id} className="flex flex-wrap items-center gap-2 text-sm">
                      {entry.fromStatus ? (
                        <>
                          <Badge variant={MOVIE_STATUS_BADGE_VARIANT[entry.fromStatus]}>
                            {MOVIE_STATUS_LABELS[entry.fromStatus]}
                          </Badge>
                          <span className="text-muted-foreground" aria-hidden>
                            →
                          </span>
                        </>
                      ) : (
                        <span className="text-xs text-muted-foreground">Created as</span>
                      )}
                      <Badge variant={MOVIE_STATUS_BADGE_VARIANT[entry.toStatus]}>
                        {MOVIE_STATUS_LABELS[entry.toStatus]}
                      </Badge>
                      <span className="text-muted-foreground">{new Date(entry.changedAt).toLocaleString("en-GB")}</span>
                      <span className="text-xs text-muted-foreground">· {entry.changedByEmail ?? "—"}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </details>
      </CardContent>
    </Card>
  );
}
