"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAuth } from "@/lib/auth/auth-context";
import { redeemTicket } from "@/lib/api/admin-tickets";
import { ApiError } from "@/lib/api/client";
import {
  redeemTicketFormSchema,
  type RedeemTicketFormInput,
  type RedeemTicketFormOutput,
} from "@/lib/validation/admin-tickets";
import { TicketRedemptionResult, type TicketRedemptionOutcome } from "@/components/admin/ticket-redemption-result";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AnimatedFieldError } from "@/components/motion/animated-field-error";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function RedeemTicketPage() {
  const { callAuthorized } = useAuth();
  const [outcome, setOutcome] = useState<TicketRedemptionOutcome | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<RedeemTicketFormInput, unknown, RedeemTicketFormOutput>({
    resolver: zodResolver(redeemTicketFormSchema),
    defaultValues: { ticketCode: "" },
  });

  // Clears and refocuses after every attempt, success or failure — this is a
  // scan-then-see-result-then-scan-next loop (handheld scanner "typing" the
  // code + Enter, or staff hand-entry), not a form the operator edits and
  // resubmits. The result panel below persists until the next submit either
  // way, so nothing about the last attempt is lost by clearing the input.
  async function onSubmit(values: RedeemTicketFormOutput) {
    try {
      const result = await callAuthorized((token) => redeemTicket(token, values.ticketCode));
      setOutcome({ kind: "success", result });
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setOutcome({ kind: "invalid", message: error.message });
      } else if (error instanceof ApiError && error.status === 409) {
        setOutcome({ kind: "conflict", message: error.message });
      } else {
        setOutcome({ kind: "network", message: "Failed to redeem. Please try again later." });
      }
    } finally {
      reset({ ticketCode: "" });
      setFocus("ticketCode");
    }
  }

  return (
    <section className="mx-auto max-w-2xl px-6 py-10">
      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">Admin · Redeem Ticket</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Check a ticket in at the door. No camera scanning here — enter the code from a
          scanner gun, or type it by hand.
        </p>
      </header>

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>Check-In</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="relative">
            <SubmitProgressBar active={isSubmitting} />
            <FieldGroup>
              <Field data-invalid={!!errors.ticketCode || undefined}>
                <FieldLabel htmlFor="ticketCode">Ticket Code</FieldLabel>
                <Input
                  id="ticketCode"
                  autoComplete="off"
                  autoFocus
                  aria-invalid={!!errors.ticketCode}
                  {...register("ticketCode")}
                />
                <FieldDescription>The QR code&apos;s raw content, or the code printed beneath it.</FieldDescription>
                <AnimatedFieldError message={errors.ticketCode?.message} />
              </Field>

              <Button type="submit" disabled={isSubmitting} className="h-11 text-base">
                {isSubmitting ? "Redeeming…" : "Redeem"}
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>

      {outcome && (
        <div className="mt-6">
          <TicketRedemptionResult outcome={outcome} />
        </div>
      )}
    </section>
  );
}
