"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { useAuth } from "@/lib/auth/auth-context";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const EASE_APPLE = [0.22, 1, 0.36, 1] as const;

export default function Home() {
  const { status } = useAuth();

  return (
    <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-4xl flex-col items-center justify-center px-6 text-center">
      <motion.p
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: EASE_APPLE }}
        className="mb-4 text-sm font-medium tracking-[0.2em] text-primary uppercase"
      >
        欢迎来到
      </motion.p>
      <motion.h1
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.05, ease: EASE_APPLE }}
        className="text-5xl font-semibold tracking-tight text-balance sm:text-6xl md:text-7xl"
      >
        属于你的<span className="text-primary">观影世界</span>
      </motion.h1>
      <motion.p
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.15, ease: EASE_APPLE }}
        className="mt-6 max-w-xl text-lg text-muted-foreground"
      >
        选座购票、影院信息、个人观影记录 — CineVerse 正在建设中,当前版本已开放账号注册与登录。
      </motion.p>
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.25, ease: EASE_APPLE }}
        className="mt-10 flex flex-col gap-3 sm:flex-row"
      >
        {status === "authenticated" ? (
          <Link
            href="/profile"
            className={cn(buttonVariants({ size: "lg" }), "h-12 px-8 text-base")}
          >
            前往我的账户
          </Link>
        ) : (
          <>
            <Link
              href="/register"
              className={cn(buttonVariants({ size: "lg" }), "h-12 px-8 text-base")}
            >
              立即注册
            </Link>
            <Link
              href="/login"
              className={cn(
                buttonVariants({ variant: "outline", size: "lg" }),
                "h-12 px-8 text-base",
              )}
            >
              登录
            </Link>
          </>
        )}
      </motion.div>
    </section>
  );
}
