package com.cineverse.backend.movie.exception;

public class MovieHasScheduledShowtimesException extends RuntimeException {

    public MovieHasScheduledShowtimesException() {
        super("Cannot delete this movie: it still has scheduled showtimes. Delete those showtimes first.");
    }
}
