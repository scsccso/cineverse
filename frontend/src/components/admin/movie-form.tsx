"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Check } from "lucide-react";
import { movieFormSchema, type MovieFormInput, type MovieFormOutput } from "@/lib/validation/admin-movies";
import type { GenreResponse, MovieRequest, MovieResponse, MovieStatus } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";

const STATUS_OPTIONS: { value: MovieStatus; label: string }[] = [
  { value: "COMING_SOON", label: "即将上映" },
  { value: "NOW_PLAYING", label: "正在热映" },
  { value: "ENDED", label: "已下映" },
];

function toDefaultValues(movie: MovieResponse | undefined): MovieFormInput {
  if (!movie) {
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

export interface MovieFormProps {
  genres: GenreResponse[];
  /** Undefined for create; the movie being edited otherwise — also used to
   * prefill genreIds, which (unlike every other field) isn't part of the
   * zod-validated schema below, see the comment on MovieFormOutput. */
  initialMovie?: MovieResponse;
  onSave: (request: MovieRequest) => Promise<MovieResponse>;
  onSaved: (saved: MovieResponse) => void;
  submitLabel: string;
  submittingLabel: string;
}

export function MovieForm({ genres, initialMovie, onSave, onSaved, submitLabel, submittingLabel }: MovieFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  // PUT replaces the movie's entire genre set — prefilling this from
  // initialMovie.genres is not cosmetic, it's what stops an edit-and-save
  // from silently wiping every genre association the movie already had
  // (see the MovieRequest doc comment in lib/api/types.ts).
  const [genreIds, setGenreIds] = useState<string[]>(() => initialMovie?.genres.map((genre) => genre.id) ?? []);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<MovieFormInput, unknown, MovieFormOutput>({
    resolver: zodResolver(movieFormSchema),
    defaultValues: toDefaultValues(initialMovie),
  });

  function toggleGenre(id: string) {
    setGenreIds((prev) => (prev.includes(id) ? prev.filter((existing) => existing !== id) : [...prev, id]));
  }

  async function onSubmit(values: MovieFormOutput) {
    setFormError(null);
    try {
      const saved = await onSave({ ...values, genreIds });
      onSaved(saved);
    } catch (error) {
      // Shown as-is, not re-worded — GlobalExceptionHandler's messages
      // (e.g. the 409 duplicate/validation cases) are already written to be
      // read by whoever is looking at the response, admin included.
      setFormError(error instanceof ApiError ? error.message : "保存失败,请稍后重试");
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="relative">
      <SubmitProgressBar active={isSubmitting} />
      <FieldGroup>
        <AnimatedFormBanner message={formError} />

        <Field data-invalid={!!errors.title || undefined}>
          <FieldLabel htmlFor="title">片名</FieldLabel>
          <Input id="title" aria-invalid={!!errors.title} {...register("title")} />
          <AnimatedFieldError message={errors.title?.message} />
        </Field>

        <Field data-invalid={!!errors.tagline || undefined}>
          <FieldLabel htmlFor="tagline">宣传语</FieldLabel>
          <Input id="tagline" placeholder="一句话文案(选填)" aria-invalid={!!errors.tagline} {...register("tagline")} />
          <AnimatedFieldError message={errors.tagline?.message} />
        </Field>

        <Field>
          <FieldLabel htmlFor="description">简介</FieldLabel>
          <textarea
            id="description"
            rows={4}
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
            {...register("description")}
          />
        </Field>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field data-invalid={!!errors.durationMinutes || undefined}>
            <FieldLabel htmlFor="durationMinutes">时长(分钟)</FieldLabel>
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
            <FieldLabel htmlFor="contentRating">分级</FieldLabel>
            <Input id="contentRating" placeholder="PG-13" aria-invalid={!!errors.contentRating} {...register("contentRating")} />
            <AnimatedFieldError message={errors.contentRating?.message} />
          </Field>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field data-invalid={!!errors.userRating || undefined}>
            <FieldLabel htmlFor="userRating">评分(0~10,选填)</FieldLabel>
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

          <Field>
            <FieldLabel htmlFor="status">状态</FieldLabel>
            <select
              id="status"
              className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
              {...register("status")}
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {!initialMovie && (
              <FieldDescription>新建电影默认「即将上映」,确认信息无误后再手动切换为「正在热映」。</FieldDescription>
            )}
          </Field>
        </div>

        <Field data-invalid={!!errors.trailerUrl || undefined}>
          <FieldLabel htmlFor="trailerUrl">预告片链接</FieldLabel>
          <Input id="trailerUrl" placeholder="https://..." aria-invalid={!!errors.trailerUrl} {...register("trailerUrl")} />
          <AnimatedFieldError message={errors.trailerUrl?.message} />
        </Field>

        <Field>
          <FieldLabel id="genres-label">分类(可多选,选填)</FieldLabel>
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
