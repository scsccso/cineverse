"use client";

import { AnimatePresence, motion } from "framer-motion";

/**
 * A thin indeterminate progress bar instead of a spinner — deliberately
 * avoided per the design brief ("不要用丑的转圈spinner").
 */
export function SubmitProgressBar({ active }: { active: boolean }) {
  return (
    <AnimatePresence>
      {active && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="absolute inset-x-0 top-0 h-0.5 overflow-hidden bg-primary/15"
        >
          <motion.div
            className="h-full w-1/3 rounded-full bg-primary"
            animate={{ x: ["-100%", "300%"] }}
            transition={{ duration: 1.1, repeat: Infinity, ease: "easeInOut" }}
          />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
