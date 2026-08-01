import Link from "next/link";
import type { Metadata } from "next";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { RegisterForm } from "@/components/auth/register-form";

export const metadata: Metadata = {
  title: "注册 · CineVerse",
};

export default function RegisterPage() {
  return (
    <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-md flex-col justify-center px-6 py-16">
      <Card className="relative overflow-hidden">
        <CardHeader>
          <CardTitle className="text-2xl">创建账号</CardTitle>
          <CardDescription>注册 CineVerse,开启你的观影世界。</CardDescription>
        </CardHeader>
        <CardContent>
          <RegisterForm />
          <p className="mt-6 text-center text-sm text-muted-foreground">
            已经有账号了?{" "}
            <Link href="/login" className="font-medium text-primary hover:underline">
              直接登录
            </Link>
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
