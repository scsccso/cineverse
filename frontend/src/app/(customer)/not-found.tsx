import Link from "next/link";
import { GlassCard } from "@/components/glass/glass-card";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Catches notFound() calls from customer pages (movies/[id], showtimes/[id]
 * — both already call it on a 404 from the API) and, best-effort, unmatched
 * URLs Next.js routes into this segment. Same layout-persists-around-it
 * reasoning as error.tsx in this same directory.
 */
export default function CustomerNotFound() {
  return (
    <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-lg flex-col justify-center px-6 py-16">
      <GlassCard className="p-8 text-center">
        <h1 className="font-display text-xl font-semibold">页面不存在</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          你要找的页面可能已经下架,或者链接有误。
        </p>
        <Link href="/" className={cn(buttonVariants(), "mt-6 h-11 w-full")}>
          返回首页
        </Link>
      </GlassCard>
    </section>
  );
}
