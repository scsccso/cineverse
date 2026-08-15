import { z } from "zod";
import { cinemaLocalTimeToIso } from "@/lib/format";

/** Mirrors admin-movies.ts's movieFormSchema pattern: native inputs still
 * hand react-hook-form strings, validated/coerced here rather than trusted
 * as already the right type. startTime is the one field that isn't a plain
 * string→string/number coercion — see cinemaLocalTimeToIso's doc comment for
 * why it needs to be interpreted as cinema-local time, not browser-local. */
export const showtimeFormSchema = z.object({
  movieId: z.string().trim().min(1, "Please select a movie"),
  hallId: z.string().trim().min(1, "Please select a hall"),
  startTime: z
    .string()
    .min(1, "Please select a start time")
    .transform((value) => cinemaLocalTimeToIso(value)),
  price: z
    .string()
    .trim()
    .min(1, "Please enter a price")
    .refine((value) => Number.isFinite(Number(value)) && Number(value) >= 0, "Price must be a number no less than 0")
    .transform((value) => Number(value)),
});

/** The raw, string-typed shape react-hook-form's registered inputs produce —
 * pass this to useForm<ShowtimeFormInput, unknown, ShowtimeFormOutput>() so
 * defaultValues/register stay string-based while onSubmit still receives the
 * validated/coerced ShowtimeFormOutput (startTime already converted to a UTC
 * ISO instant, price already a number). */
export type ShowtimeFormInput = z.input<typeof showtimeFormSchema>;
export type ShowtimeFormOutput = z.output<typeof showtimeFormSchema>;
