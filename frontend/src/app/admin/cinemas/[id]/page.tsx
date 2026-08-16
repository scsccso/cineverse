"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { createHall, getHallSeats, listCinemas, listHalls } from "@/lib/api/admin-cinemas";
import type { CinemaResponse, HallResponse } from "@/lib/api/types";
import { HallForm } from "@/components/admin/hall-form";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

interface HallSeatSummary {
  standard: number;
  couple: number;
}

export default function CinemaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { callAuthorized } = useAuth();

  const [cinema, setCinema] = useState<CinemaResponse | null>(null);
  const [halls, setHalls] = useState<HallResponse[] | null>(null);
  // Real per-hall seat data (GET /halls/{id}/seats), not a client-side
  // recompute from totalRows/totalColumns — SeatLayoutGenerator's rules
  // (last row COUPLE, paired every 2 columns) live on the backend only, and
  // re-deriving them here would drift silently if that algorithm ever
  // changed. Absent key = still loading or the fetch failed, same "don't
  // show a false 0" reasoning as the cinemas list page's hall counts.
  const [seatSummaries, setSeatSummaries] = useState<Record<string, HallSeatSummary>>({});
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    // No single-cinema GET exists — fetch the full list and find this one
    // client-side, same pattern as the showtime form's movie-status
    // filtering (see CLAUDE.md's "Admin 场次管理" decision record).
    Promise.all([listCinemas(), listHalls(id)])
      .then(([cinemas, hallList]) => {
        if (cancelled) return;
        const found = cinemas.find((c) => c.id === id);
        if (!found) {
          setLoadError(true);
          return;
        }
        setCinema(found);
        setHalls(hallList);
      })
      .catch(() => {
        if (!cancelled) setLoadError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    if (!halls || halls.length === 0) return;
    let cancelled = false;
    Promise.allSettled(halls.map((hall) => getHallSeats(hall.id).then((seats) => [hall.id, seats] as const)))
      .then((outcomes) => {
        if (cancelled) return;
        const next: Record<string, HallSeatSummary> = {};
        for (const outcome of outcomes) {
          if (outcome.status !== "fulfilled") continue;
          const [hallId, seatsResponse] = outcome.value;
          let standard = 0;
          let couple = 0;
          for (const seat of seatsResponse.seats) {
            if (seat.seatType === "COUPLE") couple += 1;
            else standard += 1;
          }
          next[hallId] = { standard, couple };
        }
        setSeatSummaries(next);
      });
    return () => {
      cancelled = true;
    };
  }, [halls]);

  function handleHallCreated(hall: HallResponse) {
    setHalls((prev) => (prev ? [...prev, hall] : [hall]));
  }

  if (loadError) {
    return (
      <section className="mx-auto max-w-3xl px-6 py-10">
        <p className="py-8 text-center text-sm text-destructive">Failed to load this cinema — it may not exist</p>
        <Link href="/admin/cinemas" className="mx-auto block w-fit text-sm text-muted-foreground hover:text-foreground">
          Back to Cinemas
        </Link>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-3xl px-6 py-10">
      <Link
        href="/admin/cinemas"
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Back to Cinemas
      </Link>

      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">{cinema ? cinema.name : "Loading…"}</h1>
        {cinema?.address && <p className="mt-1 text-sm text-muted-foreground">{cinema.address}</p>}
      </header>

      {!cinema || !halls ? (
        <p className="py-8 text-center text-sm text-muted-foreground">Loading...</p>
      ) : (
        <div className="space-y-6">
          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>Halls</CardTitle>
            </CardHeader>
            <CardContent>
              {halls.length === 0 ? (
                <div className="py-8 text-center text-sm text-muted-foreground">No halls yet</div>
              ) : (
                <div className="overflow-x-auto rounded-lg border border-border">
                  <table className="w-full text-sm">
                    <caption className="sr-only">Halls</caption>
                    <thead>
                      <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                        <th scope="col" className="px-3 py-2 font-medium">
                          Name
                        </th>
                        <th scope="col" className="px-3 py-2 font-medium">
                          Layout
                        </th>
                        <th scope="col" className="px-3 py-2 font-medium">
                          Seat Types
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {halls.map((hall) => {
                        const summary = seatSummaries[hall.id];
                        return (
                          <tr key={hall.id} className="border-b border-border last:border-0">
                            <td className="px-3 py-2 text-foreground">{hall.name}</td>
                            <td className="px-3 py-2 font-mono text-foreground">
                              {hall.totalRows} × {hall.totalColumns}
                            </td>
                            <td className="px-3 py-2">
                              {summary ? (
                                <div className="flex flex-wrap gap-1.5">
                                  <Badge variant="outline">{summary.standard} Standard</Badge>
                                  <Badge variant="outline">{summary.couple} Couple</Badge>
                                </div>
                              ) : (
                                <span className="text-muted-foreground">…</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>

          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>Add a Hall</CardTitle>
            </CardHeader>
            <CardContent>
              <HallForm
                cinemaId={id}
                onSave={(request) => callAuthorized((token) => createHall(token, id, request))}
                onSaved={handleHallCreated}
              />
            </CardContent>
          </Card>
        </div>
      )}
    </section>
  );
}
