import { z } from "zod";

/** Mirrors the backend's CreateCinemaRequest constraints (@NotBlank @Size(max=255)
 * name, @Size(max=500) address). Address is optional on the backend (no @NotBlank),
 * so an empty string here becomes null, not a validation error. */
export const cinemaFormSchema = z.object({
  name: z.string().trim().min(1, "Please enter a name").max(255, "Must be 255 characters or fewer"),
  address: z
    .string()
    .trim()
    .max(500, "Must be 500 characters or fewer")
    .transform((value) => (value.length > 0 ? value : null)),
});

export type CinemaFormInput = z.input<typeof cinemaFormSchema>;
export type CinemaFormOutput = z.output<typeof cinemaFormSchema>;

/** Mirrors the backend's CreateHallRequest constraints (@NotBlank @Size(max=255) name,
 * @NotNull @Positive @Max(40) totalRows, @NotNull @Positive @Max(50) totalColumns).
 * Native number inputs still hand react-hook-form strings — coerced here, same
 * pattern as admin-showtimes.ts's price field. */
export const hallFormSchema = z.object({
  name: z.string().trim().min(1, "Please enter a name").max(255, "Must be 255 characters or fewer"),
  totalRows: z
    .string()
    .trim()
    .min(1, "Please enter a row count")
    .refine((value) => Number.isInteger(Number(value)) && Number(value) >= 1 && Number(value) <= 40, {
      message: "Must be a whole number from 1 to 40",
    })
    .transform((value) => Number(value)),
  totalColumns: z
    .string()
    .trim()
    .min(1, "Please enter a column count")
    .refine((value) => Number.isInteger(Number(value)) && Number(value) >= 1 && Number(value) <= 50, {
      message: "Must be a whole number from 1 to 50",
    })
    .transform((value) => Number(value)),
});

export type HallFormInput = z.input<typeof hallFormSchema>;
export type HallFormOutput = z.output<typeof hallFormSchema>;
