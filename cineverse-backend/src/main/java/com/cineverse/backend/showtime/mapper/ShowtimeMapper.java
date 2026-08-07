package com.cineverse.backend.showtime.mapper;

import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.showtime.dto.ShowtimeResponse;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.storage.StorageProperties;
import java.util.List;
import org.mapstruct.Mapper;
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

    public abstract ShowtimeResponse toResponse(Showtime showtime);

    public abstract List<ShowtimeResponse> toResponseList(List<Showtime> showtimes);

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
