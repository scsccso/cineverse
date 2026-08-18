package com.cineverse.backend.movie.dto;

import com.cineverse.backend.movie.entity.MovieStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body for PATCH /api/v1/movies/{id}/status — the only path allowed to
 * change a movie's status; see MovieService.changeStatus and the guard in
 * MovieService.update() that rejects a status change arriving via PUT. */
public record UpdateMovieStatusRequest(@NotNull @Schema(example = "NOW_PLAYING") MovieStatus status) {
}
