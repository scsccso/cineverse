import Link from "next/link";
import type { Metadata } from "next";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { buttonVariants } from "@/components/ui/button";
import { RegisterForm } from "@/components/auth/register-form";
import { cn } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Sign up · CineVerse",
};

export default function RegisterPage() {
  return (
    <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-md flex-col justify-center px-6 py-16">
      <Card className="relative overflow-hidden">
        <CardHeader>
          <CardTitle className="text-2xl">Create your account</CardTitle>
          <CardDescription>Sign up for CineVerse to start booking your favorite movies.</CardDescription>
        </CardHeader>
        <CardContent>
          <RegisterForm />
          {/* Same fix as login/page.tsx — see the comment there. Reuses
              navbar.tsx's buttonVariants sizing for this same login/register
              toggle link instead of a bare unsized <Link>. */}
          <p className="mt-6 flex flex-wrap items-center justify-center gap-x-1 gap-y-1 text-center text-sm text-muted-foreground">
            <span>Already have an account?</span>
            <Link
              href="/login"
              className={cn(buttonVariants({ variant: "ghost", size: "default" }), "px-2 font-medium text-primary")}
            >
              Log in
            </Link>
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
