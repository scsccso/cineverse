package com.cineverse.backend.movie.exception;

import com.cineverse.backend.movie.entity.MovieStatus;

/** Thrown by MovieService.changeStatus() when the requested status equals
 * the movie's current status. Rejected outright rather than silently
 * treated as a no-op — a silent success here could mask a frontend bug
 * (e.g. a status option that should have been disabled) and every recorded
 * history row is meant to represent a real change. */
public class MovieStatusUnchangedException extends RuntimeException {

    public MovieStatusUnchangedException(MovieStatus status) {
        super("Movie is already " + status + ".");
    }
}
