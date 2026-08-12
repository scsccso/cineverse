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
  title: z.string().trim().min(1, "请输入片名").max(255, "片名最多 255 个字符"),
  description: z
    .string()
    .optional()
    .transform((value) => (value === undefined || value.trim() === "" ? null : value)),
  tagline: optionalText(500, "宣传语最多 500 个字符"),
  durationMinutes: z
    .string()
    .trim()
    .min(1, "请输入时长")
    .refine((value) => Number.isFinite(Number(value)) && Number(value) > 0, "时长必须是大于 0 的数字")
    .transform((value) => Math.round(Number(value))),
  contentRating: optionalText(20, "分级最多 20 个字符"),
  userRating: z
    .string()
    .trim()
    .optional()
    .refine(
      (value) =>
        value === undefined ||
        value === "" ||
        (Number.isFinite(Number(value)) && Number(value) >= 0 && Number(value) <= 10),
      "评分需在 0~10 之间",
    )
    .transform((value) => (value === undefined || value === "" ? null : Number(value))),
  trailerUrl: optionalText(500, "链接最多 500 个字符"),
  status: z.enum(MOVIE_STATUS_VALUES, { message: "请选择状态" }),
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
