import Image from "next/image";
import Link from "next/link";
import { Star } from "lucide-react";
import { GlassCard } from "@/components/glass/glass-card";
import { Badge } from "@/components/ui/badge";
import { resolveMediaUrl } from "@/lib/api/client";
import type { MovieResponse } from "@/lib/api/types";

export function MovieCard({ movie }: { movie: MovieResponse }) {
  return (
    <Link href={`/movies/${movie.id}`} className="block">
      <GlassCard>
        <div className="relative aspect-[2/3] w-full">
          <Image
            src={resolveMediaUrl(movie.posterUrl)}
            alt={movie.title}
            fill
            sizes="(max-width: 640px) 45vw, (max-width: 1024px) 30vw, 220px"
            className="object-cover transition-transform duration-300 group-hover:scale-105"
          />
        </div>
        <div className="space-y-1.5 p-4">
          <h3 className="line-clamp-1 font-display text-base font-semibold tracking-tight">
            {movie.title}
          </h3>
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            {movie.contentRating && (
              <Badge variant="outline">{movie.contentRating}</Badge>
            )}
            <span className="font-mono">{movie.durationMinutes}min</span>
            {movie.userRating && (
              <span className="ml-auto flex items-center gap-1 font-mono text-primary">
                <Star className="size-3 fill-primary" />
                {movie.userRating}
              </span>
            )}
          </div>
        </div>
      </GlassCard>
    </Link>
  );
}
