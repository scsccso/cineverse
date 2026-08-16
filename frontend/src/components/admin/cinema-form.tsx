"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cinemaFormSchema, type CinemaFormInput, type CinemaFormOutput } from "@/lib/validation/admin-cinemas";
import type { CinemaResponse, CreateCinemaRequest } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";

export interface CinemaFormProps {
  onSave: (request: CreateCinemaRequest) => Promise<CinemaResponse>;
  onSaved: (saved: CinemaResponse) => void | Promise<void>;
}

/** Create-only, no edit/delete counterpart — see CLAUDE.md Phase 3. */
export function CinemaForm({ onSave, onSaved }: CinemaFormProps) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CinemaFormInput, unknown, CinemaFormOutput>({
    resolver: zodResolver(cinemaFormSchema),
    defaultValues: { name: "", address: "" },
  });

  async function onSubmit(values: CinemaFormOutput) {
    setFormError(null);
    try {
      const saved = await onSave(values);
      reset({ name: "", address: "" });
      await onSaved(saved);
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "Failed to create. Please try again later.");
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="relative">
      <SubmitProgressBar active={isSubmitting} />
      <FieldGroup>
        <AnimatedFormBanner message={formError} />

        <Field data-invalid={!!errors.name || undefined}>
          <FieldLabel htmlFor="cinemaName">Name</FieldLabel>
          <Input id="cinemaName" aria-invalid={!!errors.name} {...register("name")} />
          <AnimatedFieldError message={errors.name?.message} />
        </Field>

        <Field data-invalid={!!errors.address || undefined}>
          <FieldLabel htmlFor="cinemaAddress">Address</FieldLabel>
          <Input id="cinemaAddress" aria-invalid={!!errors.address} {...register("address")} />
          <AnimatedFieldError message={errors.address?.message} />
        </Field>

        <Button type="submit" disabled={isSubmitting} className="h-11 text-base">
          {isSubmitting ? "Creating…" : "Create Cinema"}
        </Button>
      </FieldGroup>
    </form>
  );
}
