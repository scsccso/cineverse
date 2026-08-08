"use client";

import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { EASE_APPLE } from "@/lib/motion";

/** Field-level validation error with a height/opacity enter-exit transition. */
export function AnimatedFieldError({ message }: { message?: string }) {
  const reduceMotion = useReducedMotion();

  return (
    <AnimatePresence initial={false}>
      {message && (
        <motion.p
          key={message}
          role="alert"
          initial={{ opacity: 0, height: 0, marginTop: 0 }}
          animate={{ opacity: 1, height: "auto", marginTop: 4 }}
          exit={{ opacity: 0, height: 0, marginTop: 0 }}
          transition={{ duration: reduceMotion ? 0 : 0.18, ease: EASE_APPLE }}
          className="overflow-hidden text-sm font-normal text-destructive"
        >
          {message}
        </motion.p>
      )}
    </AnimatePresence>
  );
}
