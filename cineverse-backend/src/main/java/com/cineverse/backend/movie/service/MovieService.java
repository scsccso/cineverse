package com.cineverse.backend.movie.service;

import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.dto.MovieResponse;
import com.cineverse.backend.movie.entity.Genre;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.movie.exception.MovieHasScheduledShowtimesException;
import com.cineverse.backend.movie.mapper.MovieMapper;
import com.cineverse.backend.movie.repository.GenreRepository;
import com.cineverse.backend.movie.repository.MovieRepository;
import com.cineverse.backend.movie.repository.MovieSpecifications;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.storage.StorageService;
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

    public MovieService(
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            MovieMapper movieMapper,
            StorageService storageService,
            ShowtimeRepository showtimeRepository) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.movieMapper = movieMapper;
        this.storageService = storageService;
        this.showtimeRepository = showtimeRepository;
    }

    @Transactional(readOnly = true)
    public Page<MovieResponse> list(MovieStatus status, UUID genreId, Pageable pageable) {
        Specification<Movie> spec = Specification.where(MovieSpecifications.hasStatus(status))
                .and(MovieSpecifications.hasGenre(genreId));
        return movieRepository.findAll(spec, pageable).map(movieMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public MovieResponse getById(UUID id) {
        return movieMapper.toResponse(findMovieOrThrow(id));
    }

    @Transactional
    public MovieResponse create(MovieRequest request) {
        Movie movie = new Movie();
        applyRequest(movie, request);
        // saveAndFlush (not save): createdAt/updatedAt are populated by
        // @CreationTimestamp/@UpdateTimestamp at flush time, and the
        // response must reflect them, not null/stale.
        return movieMapper.toResponse(movieRepository.saveAndFlush(movie));
    }

    @Transactional
    public MovieResponse update(UUID id, MovieRequest request) {
        Movie movie = findMovieOrThrow(id);
        applyRequest(movie, request);
        return movieMapper.toResponse(movieRepository.saveAndFlush(movie));
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
