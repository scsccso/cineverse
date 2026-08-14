import { z } from "zod";
import { cinemaLocalTimeToIso } from "@/lib/format";

/** Mirrors admin-movies.ts's movieFormSchema pattern: native inputs still
 * hand react-hook-form strings, validated/coerced here rather than trusted
 * as already the right type. startTime is the one field that isn't a plain
 * string→string/number coercion — see cinemaLocalTimeToIso's doc comment for
 * why it needs to be interpreted as cinema-local time, not browser-local. */
export const showtimeFormSchema = z.object({
  movieId: z.string().trim().min(1, "请选择电影"),
  hallId: z.string().trim().min(1, "请选择影厅"),
  startTime: z
    .string()
    .min(1, "请选择开始时间")
    .transform((value) => cinemaLocalTimeToIso(value)),
  price: z
    .string()
    .trim()
    .min(1, "请输入价格")
    .refine((value) => Number.isFinite(Number(value)) && Number(value) >= 0, "价格必须是不小于 0 的数字")
    .transform((value) => Number(value)),
});

/** The raw, string-typed shape react-hook-form's registered inputs produce —
 * pass this to useForm<ShowtimeFormInput, unknown, ShowtimeFormOutput>() so
 * defaultValues/register stay string-based while onSubmit still receives the
 * validated/coerced ShowtimeFormOutput (startTime already converted to a UTC
 * ISO instant, price already a number). */
export type ShowtimeFormInput = z.input<typeof showtimeFormSchema>;
export type ShowtimeFormOutput = z.output<typeof showtimeFormSchema>;
