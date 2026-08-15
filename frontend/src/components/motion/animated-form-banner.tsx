"use client";

import type { ReactNode } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { CircleAlert, CircleCheck } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { EASE_APPLE } from "@/lib/motion";

interface AnimatedFormBannerProps {
  message?: string | null;
  variant?: "destructive" | "success";
  /** Optional inline call-to-action rendered below the message, e.g. the
   * movie edit page's one-time "Switch to Now Playing" suggestion right
   * after a showtime is scheduled. Every existing caller omits this and
   * renders exactly as before. */
  action?: ReactNode;
}

/** Top-of-form banner (e.g. 401 "邮箱或密码错误") with an enter/exit transition. */
export function AnimatedFormBanner({
  message,
  variant = "destructive",
  action,
}: AnimatedFormBannerProps) {
  const reduceMotion = useReducedMotion();

  return (
    <AnimatePresence initial={false}>
      {message && (
        <motion.div
          key={message}
          initial={{ opacity: 0, height: 0, marginBottom: 0 }}
          animate={{ opacity: 1, height: "auto", marginBottom: 4 }}
          exit={{ opacity: 0, height: 0, marginBottom: 0 }}
          transition={{ duration: reduceMotion ? 0 : 0.2, ease: EASE_APPLE }}
          className="overflow-hidden"
        >
          <Alert variant={variant === "destructive" ? "destructive" : "default"}>
            {variant === "destructive" ? (
              <CircleAlert className="text-destructive" />
            ) : (
              <CircleCheck className="text-primary" />
            )}
            <AlertDescription>
              {message}
              {action && <div className="mt-2">{action}</div>}
            </AlertDescription>
          </Alert>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
