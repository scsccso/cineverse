import { listMovies } from "@/lib/api/movies";
import type { MovieResponse } from "@/lib/api/types";
import { HeroCarousel } from "@/components/home/hero-carousel";
import { MovieCard } from "@/components/movies/movie-card";

export default async function Home() {
  const [nowPlaying, comingSoon] = await Promise.all([
    listMovies("NOW_PLAYING"),
    listMovies("COMING_SOON"),
  ]);

  return (
    <>
      <HeroCarousel movies={nowPlaying.content.slice(0, 5)} />
      <div className="mx-auto max-w-6xl space-y-16 px-6 py-16">
        <MovieSection
          id="now-playing"
          title="Now Playing"
          movies={nowPlaying.content}
          emptyLabel="No movies currently playing"
        />
        <MovieSection
          id="coming-soon"
          title="Coming Soon"
          movies={comingSoon.content}
          emptyLabel="No upcoming movies yet"
        />
      </div>
    </>
  );
}

function MovieSection({
  id,
  title,
  movies,
  emptyLabel,
}: {
  id: string;
  title: string;
  movies: MovieResponse[];
  emptyLabel: string;
}) {
  return (
    <section id={id} className="scroll-mt-24">
      <h2 className="font-display text-2xl font-semibold tracking-tight">
        {title}
      </h2>
      {movies.length === 0 ? (
        <p className="mt-6 text-muted-foreground">{emptyLabel}</p>
      ) : (
        <div className="mt-6 grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {movies.map((movie) => (
            <MovieCard key={movie.id} movie={movie} />
          ))}
        </div>
      )}
    </section>
  );
}
