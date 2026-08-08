"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  motion,
  useReducedMotion,
  type HTMLMotionProps,
  type MotionStyle,
} from "framer-motion";
import { cn } from "@/lib/utils";

const EASE_APPLE = [0.22, 1, 0.36, 1] as const;

type GlowStyle = MotionStyle & {
  "--glow-x"?: string;
  "--glow-y"?: string;
};

const INITIAL_GLOW: GlowStyle = {
  "--glow-x": "50%",
  "--glow-y": "25%",
};

/**
 * The signature Liquid Glass surface: every card in the app (movie,
 * showtime, and eventually seat cards) is built on this. The highlight
 * tracks the pointer via a CSS custom property updated on pointermove
 * (not a fixed-angle gradient — that's the static-glassmorphism mistake
 * this is meant to avoid). Devices without a fine hover pointer, and
 * users with prefers-reduced-motion, get a highlight fixed at the card's
 * upper-center instead of live tracking.
 */
interface GlassCardProps extends Omit<HTMLMotionProps<"div">, "children"> {
  children?: React.ReactNode;
  /**
   * Adds a press-down response on click/tap. Opt-in rather than default:
   * most GlassCards in the app are static surfaces (movie-detail info, the
   * booking countdown, the e-ticket, the expired notice) and a card that
   * shrinks under your finger reads as "this does something" — a false
   * affordance on a card that goes nowhere. Set it only where the card is
   * itself the link/button (MovieCard, ShowtimeList).
   */
  interactive?: boolean;
}

export function GlassCard({
  className,
  children,
  interactive = false,
  ...props
}: GlassCardProps) {
  const ref = useRef<HTMLDivElement>(null);
  const reduceMotion = useReducedMotion();
  const [tracksPointer, setTracksPointer] = useState(false);

  useEffect(() => {
    const query = window.matchMedia("(hover: hover) and (pointer: fine)");
    const update = () => setTracksPointer(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);

  const handlePointerMove = useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      if (!tracksPointer || reduceMotion) return;
      const el = ref.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      el.style.setProperty(
        "--glow-x",
        `${((event.clientX - rect.left) / rect.width) * 100}%`,
      );
      el.style.setProperty(
        "--glow-y",
        `${((event.clientY - rect.top) / rect.height) * 100}%`,
      );
    },
    [tracksPointer, reduceMotion],
  );

  return (
    <motion.div
      ref={ref}
      onPointerMove={handlePointerMove}
      whileHover={reduceMotion ? undefined : { scale: 1.02 }}
      // Closes the gap between mousedown and the route actually changing —
      // previously the only feedback for clicking a card was PageTransition
      // firing once navigation resolved. Gated on reduceMotion like
      // whileHover, and backstopped by the motion-reduce class below.
      whileTap={interactive && !reduceMotion ? { scale: 0.98 } : undefined}
      transition={{ duration: 0.25, ease: EASE_APPLE }}
      style={INITIAL_GLOW}
      className={cn(
        "group relative overflow-hidden rounded-3xl border border-glass-border bg-glass-surface backdrop-blur-glass shadow-[0_20px_60px_-30px_rgba(0,0,0,0.65)] transition-[border-color,box-shadow] duration-300",
        "hover:border-white/25 hover:shadow-[0_25px_70px_-25px_rgba(0,0,0,0.75)]",
        // CSS half of the two-layer reduced-motion fallback (CLAUDE.md 1.5).
        // The JS half is the useReducedMotion() gate on whileHover above;
        // this backstops it the same way the highlight layer's
        // `motion-reduce:hidden` backstops the pointermove handler, so a
        // scale added later without checking `reduceMotion` still can't
        // animate under the setting. Needs `!` because framer-motion writes
        // the scale as an inline style, which outranks a plain class.
        "motion-reduce:transform-none!",
        className,
      )}
      {...props}
    >
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100 motion-reduce:hidden [background:radial-gradient(circle_at_var(--glow-x)_var(--glow-y),var(--glass-highlight),transparent_45%)]"
      />
      <div className="relative">{children}</div>
    </motion.div>
  );
}
