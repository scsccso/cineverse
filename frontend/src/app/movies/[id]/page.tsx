import Image from "next/image";
import { notFound } from "next/navigation";
import { Star } from "lucide-react";
import { ApiError, resolveMediaUrl } from "@/lib/api/client";
import { getMovie } from "@/lib/api/movies";
import { listShowtimesByMovie } from "@/lib/api/showtimes";
import type { MovieResponse } from "@/lib/api/types";
import { GlassCard } from "@/components/glass/glass-card";
import { Badge } from "@/components/ui/badge";
import { ShowtimeList } from "@/components/showtimes/showtime-list";

async function findMovie(id: string): Promise<MovieResponse | null> {
  try {
    return await getMovie(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export default async function MovieDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const movie = await findMovie(id);
  if (!movie) {
    notFound();
  }

  const showtimes = await listShowtimesByMovie(id);

  return (
    <div>
      <div className="relative h-[50vh] min-h-[320px] w-full overflow-hidden">
        <Image
          src={resolveMediaUrl(movie.backdropUrl)}
          alt=""
          fill
          preload
          sizes="100vw"
          className="object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-background via-background/50 to-background/10" />
      </div>

      <div className="mx-auto -mt-24 max-w-5xl px-6 pb-20">
        <GlassCard className="flex flex-col gap-6 p-6 sm:flex-row sm:p-8">
          <div className="relative aspect-[2/3] w-40 shrink-0 overflow-hidden rounded-2xl sm:w-48">
            <Image
              src={resolveMediaUrl(movie.posterUrl)}
              alt={movie.title}
              fill
              sizes="192px"
              className="object-cover"
            />
          </div>
          <div className="min-w-0">
            <h1 className="font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
              {movie.title}
            </h1>
            {movie.tagline && (
              <p className="mt-2 text-muted-foreground">{movie.tagline}</p>
            )}
            <div className="mt-4 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
              {movie.contentRating && (
                <Badge variant="outline">{movie.contentRating}</Badge>
              )}
              <span className="font-mono">{movie.durationMinutes} 分钟</span>
              {movie.userRating && (
                <span className="flex items-center gap-1 text-primary">
                  <Star className="size-4 fill-primary" />
                  <span className="font-mono">{movie.userRating}</span>
                </span>
              )}
              {movie.genres.map((genre) => (
                <Badge key={genre.id} variant="secondary">
                  {genre.name}
                </Badge>
              ))}
            </div>
            {movie.description && (
              <p className="mt-4 leading-relaxed text-foreground/90">
                {movie.description}
              </p>
            )}
          </div>
        </GlassCard>

        <section className="mt-12">
          <h2 className="font-display text-2xl font-semibold tracking-tight">
            选择场次
          </h2>
          <div className="mt-6">
            <ShowtimeList showtimes={showtimes} />
          </div>
        </section>
      </div>
    </div>
  );
}
