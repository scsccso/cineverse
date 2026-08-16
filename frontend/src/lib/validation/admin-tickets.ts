import { z } from "zod";

/** Single free-text field — a scanner "types" the code and hits enter, or staff
 * paste/type it by hand. No format check beyond non-blank: the backend is the
 * only source of truth on what a valid ticket code looks like (a signed JWT
 * today, but this form shouldn't encode that assumption). */
export const redeemTicketFormSchema = z.object({
  ticketCode: z.string().trim().min(1, "Please scan or enter a ticket code"),
});

export type RedeemTicketFormInput = z.input<typeof redeemTicketFormSchema>;
export type RedeemTicketFormOutput = z.output<typeof redeemTicketFormSchema>;
