import Link from "next/link";
import type { Metadata } from "next";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { LoginForm } from "@/components/auth/login-form";

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
          <p className="mt-6 text-center text-sm text-muted-foreground">
            Don&apos;t have an account?{" "}
            <Link href="/register" className="font-medium text-primary hover:underline">
              Sign up
            </Link>
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
