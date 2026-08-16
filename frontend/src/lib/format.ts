// MVP has exactly one cinema (CineVerse Downtown, Kuala Lumpur — see
// CLAUDE.md Phase 3), so showtimes are formatted in its fixed timezone
// rather than the visitor's browser timezone: a 7pm showtime at that cinema
// is 7pm local time there regardless of where the customer is browsing
// from. Multi-cinema support would need this to come from cinema data
// instead of being hardcoded.
const CINEMA_TIME_ZONE = "Asia/Kuala_Lumpur";

export function formatShowDate(iso: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: CINEMA_TIME_ZONE,
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(new Date(iso));
}

export function formatShowTime(iso: string): string {
  return new Intl.DateTimeFormat("en-GB", {
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

/** Converts a `<input type="datetime-local">` value (e.g. "2026-09-01T14:00")
 * into the UTC instant it represents *at the cinema*, not in the browser's
 * own timezone — scheduling a showtime is an event that happens at a fixed
 * cinema-local wall-clock time, the same category as formatShowDate/
 * formatShowTime above, not a personal reference point like the payment
 * countdown (see CLAUDE.md Phase 6's G-2 decision for that distinction: the
 * payment deadline is shown in the *viewer's* device timezone on purpose,
 * this is the opposite case).
 *
 * Malaysia has used a fixed UTC+8 offset with no daylight saving time since
 * 1982, so unlike most timezones this doesn't need an Intl-based offset
 * lookup per date — the offset is a constant, same reasoning as
 * CINEMA_TIME_ZONE itself being hardcoded above. Appending it directly to
 * the datetime-local string and handing that to Date is enough; a cinema in
 * a DST-observing zone would need a real offset lookup instead. */
export function cinemaLocalTimeToIso(datetimeLocalValue: string): string {
  return new Date(`${datetimeLocalValue}:00+08:00`).toISOString();
}
