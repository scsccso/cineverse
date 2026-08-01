"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { loginSchema, type LoginFormValues } from "@/lib/validation/auth";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";

export function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
  });

  async function onSubmit(values: LoginFormValues) {
    setFormError(null);
    try {
      await login(values);
      router.push("/profile");
    } catch (error) {
      // 401 covers both "no such email" and "wrong password" — the backend
      // deliberately returns the same generic message for both so this UI
      // never leaks which one it was (anti user-enumeration).
      if (error instanceof ApiError && error.status === 401) {
        setFormError("邮箱或密码错误");
      } else {
        setFormError("登录失败,请稍后重试");
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="relative">
      <SubmitProgressBar active={isSubmitting} />
      <FieldGroup>
        <AnimatedFormBanner message={formError} />

        <Field data-invalid={!!errors.email || undefined}>
          <FieldLabel htmlFor="email">邮箱</FieldLabel>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            aria-invalid={!!errors.email}
            {...register("email")}
          />
          <AnimatedFieldError message={errors.email?.message} />
        </Field>

        <Field data-invalid={!!errors.password || undefined}>
          <FieldLabel htmlFor="password">密码</FieldLabel>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={!!errors.password}
            {...register("password")}
          />
          <AnimatedFieldError message={errors.password?.message} />
        </Field>

        <Button type="submit" disabled={isSubmitting} className="h-11 text-base">
          {isSubmitting ? "登录中…" : "登录"}
        </Button>
      </FieldGroup>
    </form>
  );
}
