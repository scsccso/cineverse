import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Catches notFound() calls from admin pages and, best-effort, unmatched
 * URLs Next.js routes into this segment. Same layout-persists-around-it
 * reasoning as app/admin/error.tsx in this same directory.
 */
export default function AdminNotFound() {
  return (
    <section className="mx-auto max-w-lg px-6 py-16">
      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>Page not found</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            The admin page you&apos;re looking for doesn&apos;t exist, or the link might be incorrect.
          </p>
          <Link href="/admin/dashboard" className={cn(buttonVariants(), "mt-6 h-11 w-full")}>
            Back to Admin Dashboard
          </Link>
        </CardContent>
      </Card>
    </section>
  );
}
