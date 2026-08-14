package com.cineverse.backend.showtime.mapper;

import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.showtime.dto.ShowtimeResponse;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.storage.StorageProperties;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Abstract class (not interface), same reason as MovieMapper: needs
 * StorageProperties injected to resolve the movie poster placeholder so
 * MovieSummary.posterUrl is never null here either.
 */
@Mapper(componentModel = "spring")
public abstract class ShowtimeMapper {

    @Autowired
    protected StorageProperties storageProperties;

    /**
     * bookedSeats/totalSeats aren't properties on {@link Showtime} itself —
     * they're pre-computed by the caller (ShowtimeService, batched across a
     * whole result list rather than queried per showtime) and threaded
     * through as explicit parameters. {@code @Mapping(source=...)} names them
     * directly rather than relying on MapStruct's implicit same-name
     * parameter matching, so there's no ambiguity to debug if that inference
     * ever doesn't kick in the way expected.
     */
    @Mapping(target = "bookedSeats", source = "bookedSeats")
    @Mapping(target = "totalSeats", source = "totalSeats")
    public abstract ShowtimeResponse toResponse(Showtime showtime, int bookedSeats, int totalSeats);

    protected ShowtimeResponse.MovieSummary toMovieSummary(Movie movie) {
        String posterUrl = (movie.getPosterUrl() != null && !movie.getPosterUrl().isBlank())
                ? movie.getPosterUrl()
                : storageProperties.defaultPosterUrl();
        String backdropUrl = (movie.getBackdropUrl() != null && !movie.getBackdropUrl().isBlank())
                ? movie.getBackdropUrl()
                : storageProperties.defaultBackdropUrl();
        return new ShowtimeResponse.MovieSummary(
                movie.getId(), movie.getTitle(), movie.getDurationMinutes(), posterUrl, backdropUrl);
    }

    protected ShowtimeResponse.HallSummary toHallSummary(Hall hall) {
        return new ShowtimeResponse.HallSummary(
                hall.getId(), hall.getName(), hall.getCinema().getId(), hall.getCinema().getName());
    }
}
