import { apiFetch } from "./client";
import type { AdminPaymentResponse, Page, PaymentStatus } from "./types";

export interface AdminPaymentSearchParams {
  status?: PaymentStatus;
  page?: number;
  size?: number;
}

/** GET /api/v1/admin/payments — ADMIN only, read-only. status is optional
 * (omit to browse every payment); there is no corresponding write/refund
 * endpoint here or planned — reconciling one of these is a manual,
 * out-of-band process, see CLAUDE.md Phase 6. */
export function searchPayments(
  token: string,
  params: AdminPaymentSearchParams = {},
): Promise<Page<AdminPaymentResponse>> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));

  return apiFetch<Page<AdminPaymentResponse>>(`/api/v1/admin/payments?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}
