"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Check } from "lucide-react";
import { movieFormSchema, type MovieFormInput, type MovieFormOutput } from "@/lib/validation/admin-movies";
import type { GenreResponse, MovieRequest, MovieResponse, TmdbMovieDetail } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { MOVIE_STATUS_OPTIONS } from "@/lib/movie-status";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";

/** initialMovie (edit) and tmdbPrefill (create, TMDB-picked) are mutually
 * exclusive — a plain blank create form has neither. contentRating/
 * userRating/status/genreIds are never sourced from tmdbPrefill even when
 * present, see TmdbMovieDetail's doc comment for why. */
function toDefaultValues(movie: MovieResponse | undefined, tmdbPrefill: TmdbMovieDetail | undefined): MovieFormInput {
  if (movie) {
    return {
      title: movie.title,
      description: movie.description ?? "",
      tagline: movie.tagline ?? "",
      durationMinutes: String(movie.durationMinutes),
      contentRating: movie.contentRating ?? "",
      userRating: movie.userRating !== null ? String(movie.userRating) : "",
      trailerUrl: movie.trailerUrl ?? "",
      status: movie.status,
    };
  }
  if (tmdbPrefill) {
    return {
      title: tmdbPrefill.title,
      description: tmdbPrefill.description ?? "",
      tagline: "",
      durationMinutes: tmdbPrefill.durationMinutes !== null ? String(tmdbPrefill.durationMinutes) : "",
      contentRating: "",
      userRating: "",
      trailerUrl: tmdbPrefill.trailerUrl ?? "",
      status: "COMING_SOON",
    };
  }
  return {
    title: "",
    description: "",
    tagline: "",
    durationMinutes: "",
    contentRating: "",
    userRating: "",
    trailerUrl: "",
    // New movies default to low visibility, not straight to NOW_PLAYING —
    // a freshly created movie shouldn't be customer-visible as "now
    // playing" until an admin has actually reviewed it (poster uploaded,
    // fields double-checked) and flips it over deliberately.
    status: "COMING_SOON",
  };
}

export interface MovieFormProps {
  genres: GenreResponse[];
  /** Undefined for create; the movie being edited otherwise — also used to
   * prefill genreIds, which (unlike every other field) isn't part of the
   * zod-validated schema below, see the comment on MovieFormOutput. */
  initialMovie?: MovieResponse;
  /** Create-only, mutually exclusive with initialMovie — prefills from a
   * TMDB search pick (admin/movies/new/page.tsx). */
  tmdbPrefill?: TmdbMovieDetail;
  onSave: (request: MovieRequest) => Promise<MovieResponse>;
  /** May return a Promise — awaited before isSubmitting/SubmitProgressBar
   * clears, so a caller that does async follow-up work (e.g. applying TMDB
   * image URLs post-create, see admin/movies/new/page.tsx) doesn't have the
   * submit button flash back to its idle state before that work — and the
   * subsequent navigation — actually finishes. A synchronous callback (the
   * edit page's usage) satisfies this signature unchanged. */
  onSaved: (saved: MovieResponse) => void | Promise<void>;
  submitLabel: string;
  submittingLabel: string;
}

export function MovieForm({
  genres,
  initialMovie,
  tmdbPrefill,
  onSave,
  onSaved,
  submitLabel,
  submittingLabel,
}: MovieFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  // PUT replaces the movie's entire genre set — prefilling this from
  // initialMovie.genres is not cosmetic, it's what stops an edit-and-save
  // from silently wiping every genre association the movie already had
  // (see the MovieRequest doc comment in lib/api/types.ts). tmdbPrefill
  // never seeds this — TMDB's genre taxonomy doesn't map onto this
  // project's fixed list, see TmdbMovieDetail's doc comment.
  const [genreIds, setGenreIds] = useState<string[]>(() => initialMovie?.genres.map((genre) => genre.id) ?? []);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<MovieFormInput, unknown, MovieFormOutput>({
    resolver: zodResolver(movieFormSchema),
    defaultValues: toDefaultValues(initialMovie, tmdbPrefill),
  });

  function toggleGenre(id: string) {
    setGenreIds((prev) => (prev.includes(id) ? prev.filter((existing) => existing !== id) : [...prev, id]));
  }

  async function onSubmit(values: MovieFormOutput) {
    setFormError(null);
    try {
      const saved = await onSave({ ...values, genreIds });
      await onSaved(saved);
    } catch (error) {
      // Shown as-is, not re-worded — GlobalExceptionHandler's messages
      // (e.g. the 409 duplicate/validation cases) are already written to be
      // read by whoever is looking at the response, admin included.
      setFormError(error instanceof ApiError ? error.message : "Failed to save. Please try again later.");
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="relative">
      <SubmitProgressBar active={isSubmitting} />
      <FieldGroup>
        <AnimatedFormBanner message={formError} />

        <Field data-invalid={!!errors.title || undefined}>
          <FieldLabel htmlFor="title">Title</FieldLabel>
          <Input id="title" aria-invalid={!!errors.title} {...register("title")} />
          <AnimatedFieldError message={errors.title?.message} />
        </Field>

        <Field data-invalid={!!errors.tagline || undefined}>
          <FieldLabel htmlFor="tagline">Tagline</FieldLabel>
          <Input id="tagline" placeholder="One-line tagline (optional)" aria-invalid={!!errors.tagline} {...register("tagline")} />
          <AnimatedFieldError message={errors.tagline?.message} />
        </Field>

        <Field>
          <FieldLabel htmlFor="description">Description</FieldLabel>
          <textarea
            id="description"
            rows={4}
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
            {...register("description")}
          />
        </Field>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field data-invalid={!!errors.durationMinutes || undefined}>
            <FieldLabel htmlFor="durationMinutes">Duration (minutes)</FieldLabel>
            <Input
              id="durationMinutes"
              type="number"
              min={1}
              inputMode="numeric"
              aria-invalid={!!errors.durationMinutes}
              {...register("durationMinutes")}
            />
            <AnimatedFieldError message={errors.durationMinutes?.message} />
          </Field>

          <Field data-invalid={!!errors.contentRating || undefined}>
            <FieldLabel htmlFor="contentRating">Content Rating</FieldLabel>
            <Input id="contentRating" placeholder="PG-13" aria-invalid={!!errors.contentRating} {...register("contentRating")} />
            <AnimatedFieldError message={errors.contentRating?.message} />
          </Field>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field data-invalid={!!errors.userRating || undefined}>
            <FieldLabel htmlFor="userRating">Rating (0–10, optional)</FieldLabel>
            <Input
              id="userRating"
              type="number"
              min={0}
              max={10}
              step={0.1}
              inputMode="decimal"
              aria-invalid={!!errors.userRating}
              {...register("userRating")}
            />
            <AnimatedFieldError message={errors.userRating?.message} />
          </Field>

          {/* Editing: status can no longer be changed through this PUT-backed
              form (see MovieRequest's doc comment) — the field still has to
              be submitted (backend requires it, and rejects a PUT whose
              status differs from the movie's current one), just not as
              something the admin can edit here. A hidden input keeps it
              registered with its unchanged defaultValue. The read-only
              display and the actual "change status" control live in
              MovieStatusControl on the edit page instead. Creating: this is
              still the only place to set the initial status. */}
          {initialMovie ? (
            <input type="hidden" {...register("status")} />
          ) : (
            <Field>
              <FieldLabel htmlFor="status">Status</FieldLabel>
              <select
                id="status"
                className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                {...register("status")}
              >
                {MOVIE_STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              <FieldDescription>New movies default to &quot;Coming Soon&quot; — switch to &quot;Now Playing&quot; manually once everything checks out.</FieldDescription>
            </Field>
          )}
        </div>

        <Field data-invalid={!!errors.trailerUrl || undefined}>
          <FieldLabel htmlFor="trailerUrl">Trailer URL</FieldLabel>
          <Input id="trailerUrl" placeholder="https://..." aria-invalid={!!errors.trailerUrl} {...register("trailerUrl")} />
          <AnimatedFieldError message={errors.trailerUrl?.message} />
        </Field>

        <Field>
          <FieldLabel id="genres-label">Genres (multi-select, optional)</FieldLabel>
          <div className="flex flex-wrap gap-2" role="group" aria-labelledby="genres-label">
            {genres.map((genre) => {
              const selected = genreIds.includes(genre.id);
              return (
                <Button
                  key={genre.id}
                  type="button"
                  variant={selected ? "default" : "outline"}
                  aria-pressed={selected}
                  onClick={() => toggleGenre(genre.id)}
                >
                  {selected && <Check className="size-4" aria-hidden />}
                  {genre.name}
                </Button>
              );
            })}
          </div>
        </Field>

        <Button type="submit" disabled={isSubmitting} className="h-11 text-base">
          {isSubmitting ? submittingLabel : submitLabel}
        </Button>
      </FieldGroup>
    </form>
  );
}
