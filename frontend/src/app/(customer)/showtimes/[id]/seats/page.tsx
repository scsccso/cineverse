import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api/client";
import { getShowtime, getShowtimeSeats } from "@/lib/api/showtimes";
import type { ShowtimeResponse } from "@/lib/api/types";
import { SeatPicker } from "@/components/booking/seat-picker";
import { formatShowDate, formatShowTime } from "@/lib/format";

async function findShowtime(id: string): Promise<ShowtimeResponse | null> {
  try {
    return await getShowtime(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export default async function SeatSelectionPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ bookingId?: string }>;
}) {
  const { id } = await params;
  const { bookingId } = await searchParams;

  const showtime = await findShowtime(id);
  if (!showtime) {
    notFound();
  }

  // Fetched server-side purely for a warm first paint — the client component
  // takes it over from here and re-fetches on its own polling interval.
  const seatData = await getShowtimeSeats(id);

  return (
    <div className="mx-auto max-w-5xl px-4 pt-10 sm:px-6">
      <SeatPicker
        showtimeId={id}
        movieTitle={showtime.movie.title}
        movieBackdropUrl={showtime.movie.backdropUrl}
        hallLabel={`${showtime.hall.name} · ${showtime.hall.cinemaName}`}
        showDate={formatShowDate(showtime.startTime)}
        showTime={formatShowTime(showtime.startTime)}
        pricePerSeat={showtime.price}
        initialSeatData={seatData}
        initialBookingId={bookingId}
      />
    </div>
  );
}
