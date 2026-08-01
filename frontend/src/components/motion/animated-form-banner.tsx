"use client";

import { AnimatePresence, motion } from "framer-motion";
import { CircleAlert, CircleCheck } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";

interface AnimatedFormBannerProps {
  message?: string | null;
  variant?: "destructive" | "success";
}

/** Top-of-form banner (e.g. 401 "邮箱或密码错误") with an enter/exit transition. */
export function AnimatedFormBanner({
  message,
  variant = "destructive",
}: AnimatedFormBannerProps) {
  return (
    <AnimatePresence initial={false}>
      {message && (
        <motion.div
          key={message}
          initial={{ opacity: 0, height: 0, marginBottom: 0 }}
          animate={{ opacity: 1, height: "auto", marginBottom: 4 }}
          exit={{ opacity: 0, height: 0, marginBottom: 0 }}
          transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
          className="overflow-hidden"
        >
          <Alert variant={variant === "destructive" ? "destructive" : "default"}>
            {variant === "destructive" ? (
              <CircleAlert className="text-destructive" />
            ) : (
              <CircleCheck className="text-primary" />
            )}
            <AlertDescription>{message}</AlertDescription>
          </Alert>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
