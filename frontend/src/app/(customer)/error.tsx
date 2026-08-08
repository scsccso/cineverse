"use client";

import { useEffect } from "react";
import { GlassCard } from "@/components/glass/glass-card";
import { Button } from "@/components/ui/button";

/**
 * Catches errors thrown anywhere under the (customer) route group.
 * app/(customer)/layout.tsx (Navbar + PageTransition) is NOT inside this
 * boundary — Next.js keeps a segment's own layout outside its error.tsx —
 * so this only needs to render the failed content area, not the page
 * chrome around it.
 */
export default function CustomerError({
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
    <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-lg flex-col justify-center px-6 py-16">
      <GlassCard className="p-8 text-center">
        <h1 className="font-display text-xl font-semibold">出了点问题</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          页面加载失败,请重试。如果问题持续出现,请稍后再来看看。
        </p>
        <Button className="mt-6 h-11 w-full" onClick={() => reset()}>
          重试
        </Button>
      </GlassCard>
    </section>
  );
}
