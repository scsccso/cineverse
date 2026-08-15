"use client";

import { useRef, useState } from "react";
import Image from "next/image";
import { Upload } from "lucide-react";
import { resolveMediaUrl } from "@/lib/api/client";
import { ApiError } from "@/lib/api/client";
import type { MovieResponse } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";

interface MovieImageUploadProps {
  label: string;
  aspect: "poster" | "backdrop";
  currentUrl: string;
  onUpload: (file: File) => Promise<MovieResponse>;
  onUploaded: (saved: MovieResponse) => void;
}

/** One poster or backdrop upload control — used twice on the edit page, once
 * per image. Both hit their own multipart endpoint (see lib/api/admin-movies
 * ts) and both require the movie to already exist, which is why this only
 * ever appears on the edit page, never the create form. */
export function MovieImageUpload({ label, aspect, currentUrl, onUpload, onUploaded }: MovieImageUploadProps) {
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  async function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Always reset the raw <input> value, success or failure — otherwise
    // re-selecting the exact same file a second time (e.g. retrying after an
    // error) wouldn't fire a new change event.
    event.target.value = "";
    if (!file) return;

    setError(null);
    setUploading(true);
    try {
      const saved = await onUpload(file);
      onUploaded(saved);
    } catch (uploadError) {
      setError(uploadError instanceof ApiError ? uploadError.message : "Upload failed. Please try again later.");
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-foreground">{label}</p>
      <div className="flex items-center gap-4">
        <div
          className={`relative overflow-hidden rounded-lg border border-border bg-muted ${
            aspect === "poster" ? "aspect-[2/3] w-24" : "aspect-video w-40"
          }`}
        >
          <Image src={resolveMediaUrl(currentUrl)} alt={label} fill sizes="200px" className="object-cover" />
        </div>
        <div className="flex flex-col gap-2">
          <input
            ref={inputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="sr-only"
            id={`upload-${aspect}`}
            onChange={handleFileChange}
          />
          <Button
            type="button"
            variant="outline"
            disabled={uploading}
            onClick={() => inputRef.current?.click()}
          >
            <Upload className="size-4" aria-hidden />
            {uploading ? "Uploading…" : "Upload New Image"}
          </Button>
          <p className="text-xs text-muted-foreground">jpg/png/webp, up to 5MB per file</p>
        </div>
      </div>
      <AnimatedFieldError message={error ?? undefined} />
    </div>
  );
}
