"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  showtimeFormSchema,
  type ShowtimeFormInput,
  type ShowtimeFormOutput,
} from "@/lib/validation/admin-showtimes";
import type { CreateShowtimeRequest, HallResponse, MovieResponse, ShowtimeResponse } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";

/** The 409 conflict message from the backend embeds a raw hall name + Instant
 * timestamps ("Conflicts with an existing showtime in Hall 1 (2026-09-01T...
 * - ...)") — a detail dump, not a message written for an admin to act on.
 * Deliberately not parsed apart to extract those details either: this
 * project has an established principle against pulling structured data out
 * of error strings (see Phase 5's seat-conflict handling in CLAUDE.md), so a
 * fixed, clear message stands in for it instead. Every other ApiError falls
 * back to MovieForm's "show ApiError.message as-is" convention — this is the
 * one deliberate exception, not a wholesale departure from that pattern. */
const CONFLICT_MESSAGE =
  "The selected time conflicts with an existing showtime in this hall (including the 20-minute changeover buffer). Please choose a different time or hall.";

export interface ShowtimeFormProps {
  /** Pre-filtered by the caller to NOW_PLAYING/COMING_SOON — see admin/showtimes/new/page.tsx. */
  movies: MovieResponse[];
  halls: HallResponse[];
  onSave: (request: CreateShowtimeRequest) => Promise<ShowtimeResponse>;
  onSaved: (saved: ShowtimeResponse) => void | Promise<void>;
}

const SELECT_CLASSNAME =
  "h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50";

/** Create-only, no edit counterpart — showtimes are create-or-delete only
 * (CLAUDE.md Phase 4), so unlike MovieForm this doesn't take an
 * initialShowtime prop or submitLabel/submittingLabel props for a second
 * caller that will never exist. */
export function ShowtimeForm({ movies, halls, onSave, onSaved }: ShowtimeFormProps) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ShowtimeFormInput, unknown, ShowtimeFormOutput>({
    resolver: zodResolver(showtimeFormSchema),
    defaultValues: { movieId: "", hallId: "", startTime: "", price: "" },
  });

  async function onSubmit(values: ShowtimeFormOutput) {
    setFormError(null);
    try {
      const saved = await onSave(values);
      await onSaved(saved);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setFormError(CONFLICT_MESSAGE);
      } else if (error instanceof ApiError) {
        setFormError(error.message);
      } else {
        setFormError("Failed to create. Please try again later.");
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="relative">
      <SubmitProgressBar active={isSubmitting} />
      <FieldGroup>
        <AnimatedFormBanner message={formError} />

        <Field data-invalid={!!errors.movieId || undefined}>
          <FieldLabel htmlFor="movieId">Movie</FieldLabel>
          <select id="movieId" aria-invalid={!!errors.movieId} className={SELECT_CLASSNAME} {...register("movieId")}>
            <option value="">Select a movie</option>
            {movies.map((movie) => (
              <option key={movie.id} value={movie.id}>
                {movie.title}
              </option>
            ))}
          </select>
          <AnimatedFieldError message={errors.movieId?.message} />
        </Field>

        <Field data-invalid={!!errors.hallId || undefined}>
          <FieldLabel htmlFor="hallId">Hall</FieldLabel>
          <select id="hallId" aria-invalid={!!errors.hallId} className={SELECT_CLASSNAME} {...register("hallId")}>
            <option value="">Select a hall</option>
            {halls.map((hall) => (
              <option key={hall.id} value={hall.id}>
                {hall.name}
              </option>
            ))}
          </select>
          <AnimatedFieldError message={errors.hallId?.message} />
        </Field>

        <Field data-invalid={!!errors.startTime || undefined}>
          <FieldLabel htmlFor="startTime">Start Time</FieldLabel>
          <Input id="startTime" type="datetime-local" aria-invalid={!!errors.startTime} {...register("startTime")} />
          <FieldDescription>Enter in the cinema&apos;s local time (Kuala Lumpur, UTC+8).</FieldDescription>
          <AnimatedFieldError message={errors.startTime?.message} />
        </Field>

        <Field data-invalid={!!errors.price || undefined}>
          <FieldLabel htmlFor="price">Price (MYR)</FieldLabel>
          <Input
            id="price"
            type="number"
            min={0}
            step={0.01}
            inputMode="decimal"
            aria-invalid={!!errors.price}
            {...register("price")}
          />
          <AnimatedFieldError message={errors.price?.message} />
        </Field>

        <Button type="submit" disabled={isSubmitting} className="h-11 text-base">
          {isSubmitting ? "Creating…" : "Create Showtime"}
        </Button>
      </FieldGroup>
    </form>
  );
}
