"use client";

import { AnimatePresence, motion, useReducedMotion } from "framer-motion";

/**
 * A thin indeterminate progress bar instead of a spinner — deliberately
 * avoided per the design brief ("不要用丑的转圈spinner").
 *
 * The inner bar's infinite left-to-right sweep is the part that actually
 * matters for prefers-reduced-motion — it's a continuous loop, not a
 * one-shot transition, which is exactly the category of motion the setting
 * exists to suppress. Under reduced motion it's replaced with a static
 * full-width bar (still communicates "submitting"), not just a
 * shorter/instant version of the same sweep.
 */
export function SubmitProgressBar({ active }: { active: boolean }) {
  const reduceMotion = useReducedMotion();

  return (
    <AnimatePresence>
      {active && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: reduceMotion ? 0 : 0.2 }}
          className="absolute inset-x-0 top-0 h-0.5 overflow-hidden bg-primary/15"
        >
          {reduceMotion ? (
            <div className="h-full w-full bg-primary" />
          ) : (
            <motion.div
              className="h-full w-1/3 rounded-full bg-primary"
              animate={{ x: ["-100%", "300%"] }}
              transition={{ duration: 1.1, repeat: Infinity, ease: "easeInOut" }}
            />
          )}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
