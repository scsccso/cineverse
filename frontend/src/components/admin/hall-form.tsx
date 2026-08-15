"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { AlertTriangle } from "lucide-react";
import { hallFormSchema, type HallFormInput, type HallFormOutput } from "@/lib/validation/admin-cinemas";
import type { CreateHallRequest, HallResponse } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";

export interface HallFormProps {
  cinemaId: string;
  onSave: (request: CreateHallRequest) => Promise<HallResponse>;
  onSaved: (saved: HallResponse) => void | Promise<void>;
}

/**
 * Create-only, no edit/delete counterpart (CLAUDE.md Phase 3) — seats are
 * generated the instant this submits (last row COUPLE, paired every 2
 * columns; everything else STANDARD — SeatLayoutGenerator, not a choice this
 * form exposes), and there is currently no API to delete a hall afterward at
 * all, so "wrong row/column count" has no fix through this system short of a
 * database change. The warning banner is the only guard against that — kept
 * inside the form itself (not a page-level aside) so it can't be dropped by
 * accident if this form is ever reused. `cinemaId` is unused by the form
 * itself; it exists so each hall's field ids stay unique if this component
 * is ever rendered more than once on the same page.
 */
export function HallForm({ cinemaId, onSave, onSaved }: HallFormProps) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<HallFormInput, unknown, HallFormOutput>({
    resolver: zodResolver(hallFormSchema),
    defaultValues: { name: "", totalRows: "", totalColumns: "" },
  });

  async function onSubmit(values: HallFormOutput) {
    setFormError(null);
    try {
      const saved = await onSave(values);
      reset({ name: "", totalRows: "", totalColumns: "" });
      await onSaved(saved);
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "Failed to create. Please try again later.");
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="relative">
      <SubmitProgressBar active={isSubmitting} />
      <FieldGroup>
        <div className="flex items-start gap-2.5 rounded-lg border border-[color:var(--chart-amber-border)] bg-[color:var(--chart-amber-surface)] p-3 text-sm">
          <AlertTriangle className="mt-0.5 size-4 shrink-0 text-[color:var(--chart-amber)]" aria-hidden />
          <p className="text-foreground">
            Seat layout is generated automatically and can&apos;t be edited afterward. There is
            currently no way to delete a hall through this system either, so double-check the row
            and column counts before creating.
          </p>
        </div>

        <AnimatedFormBanner message={formError} />

        <Field data-invalid={!!errors.name || undefined}>
          <FieldLabel htmlFor={`hallName-${cinemaId}`}>Name</FieldLabel>
          <Input id={`hallName-${cinemaId}`} aria-invalid={!!errors.name} {...register("name")} />
          <AnimatedFieldError message={errors.name?.message} />
        </Field>

        <Field data-invalid={!!errors.totalRows || undefined}>
          <FieldLabel htmlFor={`totalRows-${cinemaId}`}>Rows</FieldLabel>
          <Input
            id={`totalRows-${cinemaId}`}
            type="number"
            min={1}
            max={40}
            inputMode="numeric"
            aria-invalid={!!errors.totalRows}
            {...register("totalRows")}
          />
          <AnimatedFieldError message={errors.totalRows?.message} />
        </Field>

        <Field data-invalid={!!errors.totalColumns || undefined}>
          <FieldLabel htmlFor={`totalColumns-${cinemaId}`}>Columns</FieldLabel>
          <Input
            id={`totalColumns-${cinemaId}`}
            type="number"
            min={1}
            max={50}
            inputMode="numeric"
            aria-invalid={!!errors.totalColumns}
            {...register("totalColumns")}
          />
          <FieldDescription>
            The last row is always generated as couple seating; every other row is standard.
          </FieldDescription>
          <AnimatedFieldError message={errors.totalColumns?.message} />
        </Field>

        <Button type="submit" disabled={isSubmitting} className="h-11 text-base">
          {isSubmitting ? "Creating…" : "Create Hall"}
        </Button>
      </FieldGroup>
    </form>
  );
}
