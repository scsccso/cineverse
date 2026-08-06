import Link from "next/link";
import type { Metadata } from "next";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { LoginForm } from "@/components/auth/login-form";

export const metadata: Metadata = {
  title: "登录 · CineVerse",
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
          <CardTitle className="text-2xl">欢迎回来</CardTitle>
          <CardDescription>登录 CineVerse 账号,继续你的观影之旅。</CardDescription>
        </CardHeader>
        <CardContent>
          <LoginForm redirectTo={safeRedirect(from)} />
          <p className="mt-6 text-center text-sm text-muted-foreground">
            还没有账号?{" "}
            <Link href="/register" className="font-medium text-primary hover:underline">
              立即注册
            </Link>
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
