// MVP has exactly one cinema (CineVerse Downtown, Kuala Lumpur — see
// CLAUDE.md Phase 3), so showtimes are formatted in its fixed timezone
// rather than the visitor's browser timezone: a 7pm showtime at that cinema
// is 7pm local time there regardless of where the customer is browsing
// from. Multi-cinema support would need this to come from cinema data
// instead of being hardcoded.
const CINEMA_TIME_ZONE = "Asia/Kuala_Lumpur";

export function formatShowDate(iso: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: CINEMA_TIME_ZONE,
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(new Date(iso));
}

export function formatShowTime(iso: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: CINEMA_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
}

/** en-CA formats as YYYY-MM-DD — a stable grouping/sort key, independent of locale. */
export function showDateKey(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: CINEMA_TIME_ZONE }).format(
    new Date(iso),
  );
}
