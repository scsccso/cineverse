"use client";

import { useState } from "react";
import { Download, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { ExportFormat } from "@/lib/api/admin-reports";

interface ExportButtonsProps {
  onExport: (format: ExportFormat) => Promise<void>;
}

/** Two small download buttons; disabled + spinner while their own request is in flight (independent of the chart's own loading state). */
export function ExportButtons({ onExport }: ExportButtonsProps) {
  const [pending, setPending] = useState<ExportFormat | null>(null);
  const [error, setError] = useState(false);

  async function handleClick(format: ExportFormat) {
    setPending(format);
    setError(false);
    try {
      await onExport(format);
    } catch {
      setError(true);
    } finally {
      setPending(null);
    }
  }

  return (
    <div className="flex items-center gap-2">
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={pending !== null}
        onClick={() => handleClick("csv")}
      >
        {pending === "csv" ? (
          <Loader2 className="size-3.5 animate-spin motion-reduce:animate-none" aria-hidden data-icon="inline-start" />
        ) : (
          <Download className="size-3.5" aria-hidden data-icon="inline-start" />
        )}
        导出 CSV
      </Button>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={pending !== null}
        onClick={() => handleClick("pdf")}
      >
        {pending === "pdf" ? (
          <Loader2 className="size-3.5 animate-spin motion-reduce:animate-none" aria-hidden data-icon="inline-start" />
        ) : (
          <Download className="size-3.5" aria-hidden data-icon="inline-start" />
        )}
        导出 PDF
      </Button>
      {error && (
        <span role="alert" className="text-xs text-destructive">
          导出失败,请重试
        </span>
      )}
    </div>
  );
}
