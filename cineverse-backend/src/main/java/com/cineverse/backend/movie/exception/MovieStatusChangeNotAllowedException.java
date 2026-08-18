package com.cineverse.backend.movie.exception;

/** Thrown by MovieService.update() when a PUT's status field differs from
 * the movie's current status — PUT is a full replace of every other field,
 * but status changes must go through PATCH /movies/{id}/status so every
 * change is recorded in movie_status_history. Not a validation error (the
 * request is well-formed) — it's a policy conflict, hence 409, matching the
 * 400-vs-409 distinction this project already draws elsewhere (see
 * ticket redemption). */
public class MovieStatusChangeNotAllowedException extends RuntimeException {

    public MovieStatusChangeNotAllowedException() {
        super("Status cannot be changed via PUT — use PATCH /movies/{id}/status instead.");
    }
}
