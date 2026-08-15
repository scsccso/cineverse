import { z } from "zod";

export const loginSchema = z.object({
  email: z.email({ message: "Please enter a valid email address" }),
  password: z.string().min(1, { message: "Please enter your password" }),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  email: z.email({ message: "Please enter a valid email address" }),
  password: z
    .string()
    .min(8, { message: "Password must be at least 8 characters" })
    .max(100, { message: "Password must be at most 100 characters" }),
  fullName: z
    .string()
    .min(1, { message: "Please enter your full name" })
    .max(255, { message: "Full name must be at most 255 characters" }),
});

export type RegisterFormValues = z.infer<typeof registerSchema>;
