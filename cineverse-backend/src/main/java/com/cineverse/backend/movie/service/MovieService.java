package com.cineverse.backend.movie.service;

import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.dto.MovieResponse;
import com.cineverse.backend.movie.dto.UpdateMovieImageUrlsRequest;
import com.cineverse.backend.movie.entity.Genre;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.movie.entity.MovieStatusHistory;
import com.cineverse.backend.movie.exception.MovieHasScheduledShowtimesException;
import com.cineverse.backend.movie.exception.MovieStatusChangeNotAllowedException;
import com.cineverse.backend.movie.exception.MovieStatusUnchangedException;
import com.cineverse.backend.movie.mapper.MovieMapper;
import com.cineverse.backend.movie.repository.GenreRepository;
import com.cineverse.backend.movie.repository.MovieRepository;
import com.cineverse.backend.movie.repository.MovieSpecifications;
import com.cineverse.backend.movie.repository.MovieStatusHistoryRepository;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.storage.StorageService;
import com.cineverse.backend.user.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final MovieMapper movieMapper;
    private final StorageService storageService;
    private final ShowtimeRepository showtimeRepository;
    private final MovieStatusHistoryRepository movieStatusHistoryRepository;
    private final UserRepository userRepository;

    public MovieService(
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            MovieMapper movieMapper,
            StorageService storageService,
            ShowtimeRepository showtimeRepository,
            MovieStatusHistoryRepository movieStatusHistoryRepository,
            UserRepository userRepository) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.movieMapper = movieMapper;
        this.storageService = storageService;
        this.showtimeRepository = showtimeRepository;
        this.movieStatusHistoryRepository = movieStatusHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<MovieResponse> list(MovieStatus status, UUID genreId, String title, Pageable pageable) {
        Specification<Movie> spec = Specification.where(MovieSpecifications.hasStatus(status))
                .and(MovieSpecifications.hasGenre(genreId))
                .and(MovieSpecifications.hasTitleContaining(title));
        return movieRepository.findAll(spec, pageable).map(movieMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public MovieResponse getById(UUID id) {
        return movieMapper.toResponse(findMovieOrThrow(id));
    }

    @Transactional
    public MovieResponse create(MovieRequest request, UUID createdByUserId) {
        Movie movie = new Movie();
        applyRequest(movie, request);
        // saveAndFlush (not save): createdAt/updatedAt are populated by
        // @CreationTimestamp/@UpdateTimestamp at flush time, and the
        // response must reflect them, not null/stale.
        Movie saved = movieRepository.saveAndFlush(movie);
        // Row zero of this movie's history: an initial state, not a
        // "change" (fromStatus = null) — see MovieStatusHistory's doc
        // comment. Written in the same transaction as the movie itself so
        // a movie is never observable without at least one history row.
        recordStatusChange(saved, null, saved.getStatus(), createdByUserId);
        return movieMapper.toResponse(saved);
    }

    @Transactional
    public MovieResponse update(UUID id, MovieRequest request) {
        Movie movie = findMovieOrThrow(id);
        // Status can only change via PATCH /movies/{id}/status — the only
        // path that also writes movie_status_history. This is a
        // server-side guard, not just a UI convention: without it, anyone
        // hitting PUT directly (curl/Swagger) could change status without
        // leaving any history record at all, same reasoning as the
        // self-lockout checks in AdminUserService not trusting the
        // frontend alone.
        if (request.status() != movie.getStatus()) {
            throw new MovieStatusChangeNotAllowedException();
        }
        applyRequest(movie, request);
        return movieMapper.toResponse(movieRepository.saveAndFlush(movie));
    }

    /** The only path allowed to change a movie's status — see
     * MovieController's PATCH /{id}/status and the guard in update() above
     * that rejects a status change arriving via PUT. Same-status requests
     * are rejected (409), not silently treated as a no-op: every row in
     * movie_status_history is meant to represent a real change, and a
     * silent success here could mask a frontend bug. There is no
     * restriction on *which* transitions are allowed beyond that — e.g.
     * ENDED -> NOW_PLAYING (a theatrical re-release) is a legitimate
     * real-world scenario, and PATCH is now the only route to reach any
     * status at all, so blocking a transition here would leave no path
     * forward for it. */
    @Transactional
    public MovieResponse changeStatus(UUID id, MovieStatus newStatus, UUID changedByUserId) {
        Movie movie = findMovieOrThrow(id);
        MovieStatus previousStatus = movie.getStatus();
        if (previousStatus == newStatus) {
            throw new MovieStatusUnchangedException(newStatus);
        }
        movie.setStatus(newStatus);
        Movie saved = movieRepository.saveAndFlush(movie);
        recordStatusChange(saved, previousStatus, newStatus, changedByUserId);
        return movieMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Movie movie = findMovieOrThrow(id);
        // Checked here (not left to the FK) so a mis-click gets a clean 409
        // instead of a raw DB constraint error; the showtimes.movie_id FK is
        // ON DELETE RESTRICT (V8) as a defense-in-depth backstop, not the
        // primary guard — deleting a movie must never cascade-delete its
        // showtimes.
        if (showtimeRepository.existsByMovieId(id)) {
            throw new MovieHasScheduledShowtimesException();
        }
        storageService.delete(movie.getPosterUrl());
        storageService.delete(movie.getBackdropUrl());
        movieRepository.delete(movie);
    }

    @Transactional
    public MovieResponse updatePoster(UUID id, MultipartFile file) {
        Movie movie = findMovieOrThrow(id);
        String oldUrl = movie.getPosterUrl();
        movie.setPosterUrl(storageService.store(file));
        movieRepository.saveAndFlush(movie);
        storageService.delete(oldUrl);
        return movieMapper.toResponse(movie);
    }

    @Transactional
    public MovieResponse updateBackdrop(UUID id, MultipartFile file) {
        Movie movie = findMovieOrThrow(id);
        String oldUrl = movie.getBackdropUrl();
        movie.setBackdropUrl(storageService.store(file));
        movieRepository.saveAndFlush(movie);
        storageService.delete(oldUrl);
        return movieMapper.toResponse(movie);
    }

    /** Sets whichever of posterUrl/backdropUrl are present directly on the
     * entity — no StorageService.store() call, this hotlinks an external
     * URL (e.g. from the TMDB search flow) rather than uploading a file.
     * Still routes the old value through storageService.delete() first,
     * same as updatePoster/updateBackdrop: it's a safe no-op for a URL that
     * was never locally stored (see LocalStorageService.delete's baseUrl
     * prefix check), and cleans up an orphaned local file if the movie
     * previously had one. */
    @Transactional
    public MovieResponse updateImageUrls(UUID id, UpdateMovieImageUrlsRequest request) {
        Movie movie = findMovieOrThrow(id);
        if (request.posterUrl() != null) {
            storageService.delete(movie.getPosterUrl());
            movie.setPosterUrl(request.posterUrl());
        }
        if (request.backdropUrl() != null) {
            storageService.delete(movie.getBackdropUrl());
            movie.setBackdropUrl(request.backdropUrl());
        }
        movieRepository.saveAndFlush(movie);
        return movieMapper.toResponse(movie);
    }

    /** userRepository.getReferenceById avoids an extra SELECT to load the
     * full User row just to set a FK — the caller already has a trusted
     * UUID from the JWT subject (see MovieController). */
    private void recordStatusChange(Movie movie, MovieStatus fromStatus, MovieStatus toStatus, UUID changedByUserId) {
        movieStatusHistoryRepository.saveAndFlush(
                new MovieStatusHistory(movie, fromStatus, toStatus, userRepository.getReferenceById(changedByUserId)));
    }

    private Movie findMovieOrThrow(UUID id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));
    }

    private void applyRequest(Movie movie, MovieRequest request) {
        movie.setTitle(request.title());
        movie.setDescription(request.description());
        movie.setTagline(request.tagline());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setContentRating(request.contentRating());
        movie.setUserRating(request.userRating());
        movie.setTrailerUrl(request.trailerUrl());
        movie.setStatus(request.status());
        movie.setGenres(resolveGenres(request.genreIds()));
    }

    private Set<Genre> resolveGenres(Set<UUID> genreIds) {
        if (genreIds == null || genreIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Genre> found = genreRepository.findAllById(genreIds);
        if (found.size() != genreIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more genre IDs do not exist");
        }
        return new LinkedHashSet<>(found);
    }
}
