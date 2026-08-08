"use client";

import { useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

/**
 * Catches errors thrown by pages under app/admin/**. app/admin/layout.tsx
 * (AdminHeader + the .admin-light wrapper) is NOT inside this boundary —
 * same Next.js layout-persists-around-error.tsx semantics as the customer
 * route group's error.tsx — so this only needs Card-based content; it
 * doesn't need to redeclare the light theme or the header itself.
 */
export default function AdminError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <section className="mx-auto max-w-lg px-6 py-16">
      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>出了点问题</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            页面加载失败,请重试。如果问题持续出现,请检查后端服务是否正常运行。
          </p>
          <Button className="mt-6 h-11 w-full" onClick={() => reset()}>
            重试
          </Button>
        </CardContent>
      </Card>
    </section>
  );
}
