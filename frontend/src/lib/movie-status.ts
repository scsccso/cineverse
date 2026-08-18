import type { MovieStatus } from "@/lib/api/types";

/** Single source of truth for how a MovieStatus reads/looks across the
 * admin UI (movies list badges, the edit page's read-only status display,
 * the status history timeline, the create form's picker) — three-plus call
 * sites needing the exact same label/variant is the point where keeping
 * them in sync by hand risks a real, user-visible drift (a status reading
 * differently on one page than another), not premature abstraction. */
export const MOVIE_STATUS_LABELS: Record<MovieStatus, string> = {
  NOW_PLAYING: "Now Playing",
  COMING_SOON: "Coming Soon",
  ENDED: "Ended",
};

/** Badge variant differs by status (default/secondary/outline) — a visual
 * treatment, not just a color swap, so this already satisfies "don't
 * distinguish state by color alone" without a separate icon. */
export const MOVIE_STATUS_BADGE_VARIANT: Record<MovieStatus, "default" | "secondary" | "outline"> = {
  NOW_PLAYING: "default",
  COMING_SOON: "secondary",
  ENDED: "outline",
};

export const MOVIE_STATUS_OPTIONS: { value: MovieStatus; label: string }[] = (
  ["COMING_SOON", "NOW_PLAYING", "ENDED"] as const
).map((value) => ({ value, label: MOVIE_STATUS_LABELS[value] }));
