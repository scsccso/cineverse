/** en-MY renders MYR as "RM75.00" — matches the cinema's one location (Kuala Lumpur, see CLAUDE.md Phase 3), not the admin's browser locale. */
export function formatCurrency(amount: number, currency: string, options?: { compact?: boolean }): string {
  const fractionDigits = options?.compact ? 0 : 2;
  return new Intl.NumberFormat("en-MY", {
    style: "currency",
    currency: currency.toUpperCase(),
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(amount);
}

/** rate is 0..1, as returned by the occupancy report. */
export function formatPercent(rate: number): string {
  return new Intl.NumberFormat("en-MY", { style: "percent", maximumFractionDigits: 1 }).format(rate);
}
