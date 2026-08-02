"use client";

import { useCallback, useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { ChevronLeft, ChevronRight, Star } from "lucide-react";
import { GlassCard } from "@/components/glass/glass-card";
import { buttonVariants } from "@/components/ui/button";
import { resolveMediaUrl } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import type { MovieResponse } from "@/lib/api/types";

const EASE_APPLE = [0.22, 1, 0.36, 1] as const;
const AUTO_ADVANCE_MS = 7000;

export function HeroCarousel({ movies }: { movies: MovieResponse[] }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduceMotion = useReducedMotion();

  const goTo = useCallback(
    (next: number) => setIndex((next + movies.length) % movies.length),
    [movies.length],
  );

  useEffect(() => {
    if (paused || reduceMotion || movies.length <= 1) return;
    const id = setInterval(
      () => setIndex((current) => (current + 1) % movies.length),
      AUTO_ADVANCE_MS,
    );
    return () => clearInterval(id);
  }, [paused, reduceMotion, movies.length]);

  if (movies.length === 0) {
    return null;
  }

  const movie = movies[index];

  return (
    <section
      className="relative h-[70vh] min-h-[420px] w-full overflow-hidden"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
    >
      <AnimatePresence mode="wait">
        <motion.div
          key={movie.id}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: reduceMotion ? 0 : 0.6, ease: EASE_APPLE }}
          className="absolute inset-0"
        >
          <Image
            src={resolveMediaUrl(movie.backdropUrl)}
            alt=""
            fill
            preload={index === 0}
            sizes="100vw"
            className="object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-background via-background/40 to-background/10" />
        </motion.div>
      </AnimatePresence>

      <div className="relative z-10 flex h-full items-end">
        <div className="mx-auto w-full max-w-6xl px-6 pb-16">
          <GlassCard className="max-w-xl p-6 sm:p-8">
            {movie.contentRating && (
              <span className="mb-3 inline-block rounded-full border border-glass-border px-2.5 py-0.5 text-xs text-muted-foreground">
                {movie.contentRating}
              </span>
            )}
            <h1 className="font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
              {movie.title}
            </h1>
            <div className="mt-3 flex items-center gap-4 text-sm text-muted-foreground">
              <span className="font-mono">{movie.durationMinutes} min</span>
              {movie.userRating && (
                <span className="flex items-center gap-1 text-primary">
                  <Star className="size-4 fill-primary" />
                  <span className="font-mono">{movie.userRating}</span>
                </span>
              )}
            </div>
            {movie.tagline && (
              <p className="mt-4 line-clamp-2 text-muted-foreground">
                {movie.tagline}
              </p>
            )}
            <Link
              href={`/movies/${movie.id}`}
              className={cn(buttonVariants({ size: "lg" }), "mt-6 h-12 px-8")}
            >
              立即购票
            </Link>
          </GlassCard>
        </div>
      </div>

      {movies.length > 1 && (
        <>
          <CarouselArrow direction="prev" onClick={() => goTo(index - 1)} />
          <CarouselArrow direction="next" onClick={() => goTo(index + 1)} />
          <div className="absolute inset-x-0 bottom-6 z-10 flex justify-center gap-1">
            {movies.map((candidate, i) => (
              <button
                key={candidate.id}
                type="button"
                aria-label={`第 ${i + 1} 部影片`}
                aria-current={i === index}
                onClick={() => goTo(i)}
                className="flex h-11 w-11 items-center justify-center"
              >
                <span
                  className={cn(
                    "block h-2 rounded-full border border-glass-border bg-glass-surface backdrop-blur-glass transition-all duration-300",
                    i === index ? "w-6 bg-primary/80" : "w-2",
                  )}
                />
              </button>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function CarouselArrow({
  direction,
  onClick,
}: {
  direction: "prev" | "next";
  onClick: () => void;
}) {
  const Icon = direction === "prev" ? ChevronLeft : ChevronRight;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={direction === "prev" ? "上一部" : "下一部"}
      className={cn(
        "absolute top-1/2 z-10 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full border border-glass-border bg-glass-surface backdrop-blur-glass text-foreground transition-colors hover:bg-white/15",
        direction === "prev" ? "left-4 sm:left-8" : "right-4 sm:right-8",
      )}
    >
      <Icon className="size-5" />
    </button>
  );
}
