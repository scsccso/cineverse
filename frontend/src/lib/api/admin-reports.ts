import { apiFetch } from "./client";
import type { OccupancyReportResponse, ReportGranularity, SalesReportResponse } from "./types";

// 8081, not 8080 — see lib/api/client.ts's API_BASE_URL comment and
// docs/DEVELOPMENT.md's port-conflict note.
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

export function getSalesReport(
  accessToken: string,
  params: { from: string; to: string; granularity: ReportGranularity },
): Promise<SalesReportResponse> {
  const query = new URLSearchParams({ from: params.from, to: params.to, granularity: params.granularity });
  return apiFetch<SalesReportResponse>(`/api/v1/admin/reports/sales?${query}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

export function getOccupancyReport(
  accessToken: string,
  params: { from: string; to: string },
): Promise<OccupancyReportResponse> {
  const query = new URLSearchParams({ from: params.from, to: params.to });
  return apiFetch<OccupancyReportResponse>(`/api/v1/admin/reports/occupancy?${query}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

export type ExportFormat = "csv" | "pdf";

/**
 * Export responses are a file body, not JSON, so this bypasses apiFetch and
 * drives the browser download directly: fetch as a Blob, read the filename
 * the backend already chose off Content-Disposition (falling back only if
 * that header is somehow missing), and click a throwaway <a download> to
 * trigger the save — the standard no-library way to turn a fetched Blob into
 * a download in a browser that already has the file via fetch (no second
 * network round-trip through a plain <a href> navigation).
 */
async function downloadExport(accessToken: string, path: string, filenameFallback: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(`Export failed with status ${response.status}`);
  }
  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition");
  const filename = disposition?.match(/filename="([^"]+)"/)?.[1] ?? filenameFallback;

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function exportSalesReport(
  accessToken: string,
  params: { from: string; to: string; granularity: ReportGranularity; format: ExportFormat },
): Promise<void> {
  const query = new URLSearchParams({
    from: params.from,
    to: params.to,
    granularity: params.granularity,
    format: params.format,
  });
  return downloadExport(accessToken, `/api/v1/admin/reports/sales/export?${query}`, `sales-report.${params.format}`);
}

export function exportOccupancyReport(
  accessToken: string,
  params: { from: string; to: string; format: ExportFormat },
): Promise<void> {
  const query = new URLSearchParams({ from: params.from, to: params.to, format: params.format });
  return downloadExport(
    accessToken,
    `/api/v1/admin/reports/occupancy/export?${query}`,
    `occupancy-report.${params.format}`,
  );
}
