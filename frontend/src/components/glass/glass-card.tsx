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
}

export function GlassCard({ className, children, ...props }: GlassCardProps) {
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
      transition={{ duration: 0.25, ease: EASE_APPLE }}
      style={INITIAL_GLOW}
      className={cn(
        "group relative overflow-hidden rounded-3xl border border-glass-border bg-glass-surface backdrop-blur-glass shadow-[0_20px_60px_-30px_rgba(0,0,0,0.65)] transition-[border-color,box-shadow] duration-300",
        "hover:border-white/25 hover:shadow-[0_25px_70px_-25px_rgba(0,0,0,0.75)]",
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
