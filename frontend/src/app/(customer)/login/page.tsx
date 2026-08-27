import Link from "next/link";
import type { Metadata } from "next";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { buttonVariants } from "@/components/ui/button";
import { LoginForm } from "@/components/auth/login-form";
import { cn } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Log in · CineVerse",
};

/** Only accept same-origin relative paths — a bare "from" query param is otherwise an open-redirect vector. */
function safeRedirect(from: string | undefined): string {
  if (from && from.startsWith("/") && !from.startsWith("//")) {
    return from;
  }
  return "/profile";
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ from?: string }>;
}) {
  const { from } = await searchParams;

  return (
    <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-md flex-col justify-center px-6 py-16">
      <Card className="relative overflow-hidden">
        <CardHeader>
          <CardTitle className="text-2xl">Welcome back</CardTitle>
          <CardDescription>Log in to your CineVerse account to continue booking movies.</CardDescription>
        </CardHeader>
        <CardContent>
          <LoginForm redirectTo={safeRedirect(from)} />
          {/* Was a bare inline <Link> with no size rule — measured at 17px
              tall, far under the project's 44x44 (h-11) touch-target
              standard. Fixed by reusing the same buttonVariants sizing
              navbar.tsx already applies to its own login/register links
              (see AuthSection there), not a new styling approach: `ghost` +
              `default` gives the h-11 hit target while staying background-
              free at rest, so it still reads as a plain text link, just a
              tappable one. The row is a flex container (not raw inline
              text) so the now-taller Link centers predictably against the
              question text instead of stretching the paragraph's line box. */}
          <p className="mt-6 flex flex-wrap items-center justify-center gap-x-1 gap-y-1 text-center text-sm text-muted-foreground">
            <span>Don&apos;t have an account?</span>
            <Link
              href="/register"
              className={cn(buttonVariants({ variant: "ghost", size: "default" }), "px-2 font-medium text-primary")}
            >
              Sign up
            </Link>
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
