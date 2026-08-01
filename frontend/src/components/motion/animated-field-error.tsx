"use client";

import { AnimatePresence, motion } from "framer-motion";

/** Field-level validation error with a height/opacity enter-exit transition. */
export function AnimatedFieldError({ message }: { message?: string }) {
  return (
    <AnimatePresence initial={false}>
      {message && (
        <motion.p
          key={message}
          role="alert"
          initial={{ opacity: 0, height: 0, marginTop: 0 }}
          animate={{ opacity: 1, height: "auto", marginTop: 4 }}
          exit={{ opacity: 0, height: 0, marginTop: 0 }}
          transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
          className="overflow-hidden text-sm font-normal text-destructive"
        >
          {message}
        </motion.p>
      )}
    </AnimatePresence>
  );
}
