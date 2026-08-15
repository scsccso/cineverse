"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-context";
import { createCinema, listCinemas, listHalls } from "@/lib/api/admin-cinemas";
import type { CinemaResponse } from "@/lib/api/types";
import { CinemaForm } from "@/components/admin/cinema-form";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export default function AdminCinemasPage() {
  const { callAuthorized } = useAuth();
  const [cinemas, setCinemas] = useState<CinemaResponse[] | null>(null);
  // Absent key = still loading (or the per-cinema count fetch failed) — kept
  // distinct from a confirmed 0, same reasoning as ScheduleShowtimesNudge's
  // upcomingShowtimeCount: a wrong "0 halls" badge is worse than a "…" one.
  const [hallCounts, setHallCounts] = useState<Record<string, number>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listCinemas()
      .then(async (list) => {
        if (cancelled) return;
        setCinemas(list);
        setError(null);

        const outcomes = await Promise.allSettled(
          list.map((cinema) => listHalls(cinema.id).then((halls) => [cinema.id, halls.length] as const)),
        );
        if (cancelled) return;
        const nextCounts: Record<string, number> = {};
        for (const outcome of outcomes) {
          if (outcome.status === "fulfilled") {
            const [cinemaId, count] = outcome.value;
            nextCounts[cinemaId] = count;
          }
        }
        setHallCounts(nextCounts);
      })
      .catch(() => {
        if (!cancelled) {
          setCinemas(null);
          setError("Failed to load cinemas");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function handleCreated(cinema: CinemaResponse) {
    setCinemas((prev) => (prev ? [...prev, cinema] : [cinema]));
    setHallCounts((prev) => ({ ...prev, [cinema.id]: 0 }));
  }

  return (
    <section className="mx-auto max-w-3xl px-6 py-10">
      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">Admin · Cinemas</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Branches and their halls. Open a branch to see and add its halls.
        </p>
      </header>

      {error && (
        <div role="alert" className="mb-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="space-y-6">
        <Card className="shadow-sm">
          <CardHeader>
            <CardTitle>Branches</CardTitle>
          </CardHeader>
          <CardContent>
            {!cinemas ? (
              <div className="py-8 text-center text-sm text-muted-foreground">Loading...</div>
            ) : cinemas.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">No cinemas yet</div>
            ) : (
              <ul className="divide-y divide-border">
                {cinemas.map((cinema) => {
                  const hallCount = hallCounts[cinema.id];
                  return (
                    <li key={cinema.id}>
                      <Link
                        href={`/admin/cinemas/${cinema.id}`}
                        className="-mx-3 flex items-center justify-between gap-4 rounded-lg px-3 py-3 transition-colors hover:bg-secondary/50"
                      >
                        <div>
                          <p className="font-medium text-foreground">{cinema.name}</p>
                          {cinema.address && <p className="text-sm text-muted-foreground">{cinema.address}</p>}
                        </div>
                        <Badge variant="outline">
                          {hallCount === undefined ? "…" : `${hallCount} ${hallCount === 1 ? "hall" : "halls"}`}
                        </Badge>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader>
            <CardTitle>Add a Cinema</CardTitle>
          </CardHeader>
          <CardContent>
            <CinemaForm
              onSave={(request) => callAuthorized((token) => createCinema(token, request))}
              onSaved={handleCreated}
            />
          </CardContent>
        </Card>
      </div>
    </section>
  );
}
