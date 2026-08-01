import { z } from "zod";

export const loginSchema = z.object({
  email: z.email({ message: "请输入有效的邮箱地址" }),
  password: z.string().min(1, { message: "请输入密码" }),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  email: z.email({ message: "请输入有效的邮箱地址" }),
  password: z
    .string()
    .min(8, { message: "密码至少需要 8 个字符" })
    .max(100, { message: "密码最多 100 个字符" }),
  fullName: z
    .string()
    .min(1, { message: "请输入姓名" })
    .max(255, { message: "姓名最多 255 个字符" }),
});

export type RegisterFormValues = z.infer<typeof registerSchema>;
