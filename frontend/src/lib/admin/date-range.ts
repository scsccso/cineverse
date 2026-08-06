// Same fixed cinema timezone as lib/format.ts's CINEMA_TIME_ZONE (MVP has
// exactly one cinema — see CLAUDE.md Phase 3) — re-declared here rather than
// imported since format.ts doesn't export it and this is the only other
// place that needs it. "Today" for report presets means the cinema's
// calendar day, matching how the backend buckets reports (ReportService.
// CINEMA_ZONE, same "Asia/Kuala_Lumpur" value).
const CINEMA_TIME_ZONE = "Asia/Kuala_Lumpur";

export type DateRangePreset = "today" | "7d" | "30d" | "custom";

export interface DateRange {
  from: string;
  to: string;
}

/** en-CA formats as YYYY-MM-DD — matches lib/format.ts's showDateKey technique. */
function todayInCinemaZone(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: CINEMA_TIME_ZONE }).format(new Date());
}

/** isoDate is a plain YYYY-MM-DD calendar date — subtracted as a date, not a timestamp, so there's no time-of-day/DST ambiguity to reason about. */
function subtractDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}

/** "Last N days" is inclusive of today (N-1 days back through today = N calendar days total). */
export function resolvePreset(preset: Exclude<DateRangePreset, "custom">): DateRange {
  const to = todayInCinemaZone();
  switch (preset) {
    case "today":
      return { from: to, to };
    case "7d":
      return { from: subtractDays(to, 6), to };
    case "30d":
      return { from: subtractDays(to, 29), to };
  }
}
