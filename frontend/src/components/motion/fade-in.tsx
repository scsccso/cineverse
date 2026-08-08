"use client";

import { motion, useReducedMotion, type HTMLMotionProps } from "framer-motion";
import { EASE_APPLE } from "@/lib/motion";

interface FadeInProps extends Omit<HTMLMotionProps<"div">, "initial" | "animate" | "transition"> {
  /** Starting vertical offset in px before the fade-in settles to 0. */
  y?: number;
  /** Transition duration in seconds. */
  duration?: number;
}

/**
 * The opacity+y entrance shared by the profile page, the e-ticket page, and
 * the register-form success state — three near-identical `motion.div`s that
 * used to each hand-roll the same shape and none of them checked
 * `prefers-reduced-motion`. Follows the same "zero out the offset/duration"
 * pattern already used elsewhere in this codebase (HeroCarousel,
 * PageTransition) rather than framer-motion's `initial={false}` idiom, so
 * there's one convention for this, not two.
 */
export function FadeIn({ y = 12, duration = 0.4, children, ...props }: FadeInProps) {
  const reduceMotion = useReducedMotion();

  return (
    <motion.div
      initial={{ opacity: 0, y: reduceMotion ? 0 : y }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: reduceMotion ? 0 : duration, ease: EASE_APPLE }}
      {...props}
    >
      {children}
    </motion.div>
  );
}
