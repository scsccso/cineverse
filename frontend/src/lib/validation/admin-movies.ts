import { z } from "zod";

const MOVIE_STATUS_VALUES = ["NOW_PLAYING", "COMING_SOON", "ENDED"] as const;

/** Empty-string-or-blank optional text field, normalized to null (the shape
 * MovieRequest's nullable string fields expect) rather than "". */
function optionalText(max: number, message: string) {
  return z
    .string()
    .trim()
    .max(max, message)
    .optional()
    .transform((value) => (value === undefined || value === "" ? null : value));
}

/** Native number inputs still hand react-hook-form a string — validated and
 * coerced here rather than trusted as already-numeric, since durationMinutes
 * is required (no fallback) and userRating is optional (blank = no rating,
 * not 0 — 0 is itself a valid, if unusual, rating). */
export const movieFormSchema = z.object({
  title: z.string().trim().min(1, "Please enter a title").max(255, "Title must be at most 255 characters"),
  description: z
    .string()
    .optional()
    .transform((value) => (value === undefined || value.trim() === "" ? null : value)),
  tagline: optionalText(500, "Tagline must be at most 500 characters"),
  durationMinutes: z
    .string()
    .trim()
    .min(1, "Please enter a duration")
    .refine((value) => Number.isFinite(Number(value)) && Number(value) > 0, "Duration must be a number greater than 0")
    .transform((value) => Math.round(Number(value))),
  contentRating: optionalText(20, "Content rating must be at most 20 characters"),
  userRating: z
    .string()
    .trim()
    .optional()
    .refine(
      (value) =>
        value === undefined ||
        value === "" ||
        (Number.isFinite(Number(value)) && Number(value) >= 0 && Number(value) <= 10),
      "Rating must be between 0 and 10",
    )
    .transform((value) => (value === undefined || value === "" ? null : Number(value))),
  trailerUrl: optionalText(500, "URL must be at most 500 characters"),
  status: z.enum(MOVIE_STATUS_VALUES, { message: "Please select a status" }),
});

/** The raw, string-typed shape react-hook-form's registered inputs produce —
 * pass this to useForm<MovieFormInput, unknown, MovieFormOutput>() so
 * defaultValues/register stay string-based while onSubmit still receives the
 * validated/coerced MovieFormOutput. */
export type MovieFormInput = z.input<typeof movieFormSchema>;
/** genreIds is deliberately not part of this schema — it's a multi-select
 * toggle-button group, not a registered text input, so the form component
 * tracks it as separate React state and merges it into the payload at
 * submit time instead of fighting react-hook-form over an array field. */
export type MovieFormOutput = z.output<typeof movieFormSchema>;
