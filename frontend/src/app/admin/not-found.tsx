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
          <CardTitle>页面不存在</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            你要找的管理页面不存在,或者链接有误。
          </p>
          <Link href="/admin/dashboard" className={cn(buttonVariants(), "mt-6 h-11 w-full")}>
            返回管理后台首页
          </Link>
        </CardContent>
      </Card>
    </section>
  );
}
