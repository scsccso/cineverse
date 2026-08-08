"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { CircleCheck } from "lucide-react";
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { registerSchema, type RegisterFormValues } from "@/lib/validation/auth";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button, buttonVariants } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";
import { FadeIn } from "@/components/motion/fade-in";
import { cn } from "@/lib/utils";

export function RegisterForm() {
  const { register: registerUser } = useAuth();
  const [formError, setFormError] = useState<string | null>(null);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
  });

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null);
    try {
      const user = await registerUser(values);
      setRegisteredEmail(user.email);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setFormError("该邮箱已被注册");
      } else {
        setFormError("注册失败,请稍后重试");
      }
    }
  }

  if (registeredEmail) {
    return (
      <FadeIn y={8} duration={0.3} className="flex flex-col items-center gap-4 py-4 text-center">
        <CircleCheck className="size-10 text-primary" />
        <div>
          <p className="font-medium">注册成功</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {registeredEmail} 已开通,现在可以登录了。
          </p>
        </div>
        <Link href="/login" className={cn(buttonVariants(), "h-11 w-full text-base")}>
          前往登录
        </Link>
      </FadeIn>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="relative">
      <SubmitProgressBar active={isSubmitting} />
      <FieldGroup>
        <AnimatedFormBanner message={formError} />

        <Field data-invalid={!!errors.fullName || undefined}>
          <FieldLabel htmlFor="fullName">姓名</FieldLabel>
          <Input
            id="fullName"
            type="text"
            autoComplete="name"
            placeholder="Jane Doe"
            aria-invalid={!!errors.fullName}
            {...register("fullName")}
          />
          <AnimatedFieldError message={errors.fullName?.message} />
        </Field>

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
            autoComplete="new-password"
            aria-invalid={!!errors.password}
            {...register("password")}
          />
          <AnimatedFieldError message={errors.password?.message} />
        </Field>

        <Button type="submit" disabled={isSubmitting} className="h-11 text-base">
          {isSubmitting ? "注册中…" : "创建账号"}
        </Button>
      </FieldGroup>
    </form>
  );
}
