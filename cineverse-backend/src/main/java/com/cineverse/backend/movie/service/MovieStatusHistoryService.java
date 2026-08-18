package com.cineverse.backend.movie.service;

import com.cineverse.backend.movie.dto.MovieStatusHistoryEntryResponse;
import com.cineverse.backend.movie.mapper.MovieStatusHistoryMapper;
import com.cineverse.backend.movie.repository.MovieRepository;
import com.cineverse.backend.movie.repository.MovieStatusHistoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Backs GET /api/v1/admin/movies/{id}/status-history — read-only, kept
 * separate from MovieService the same way AdminPaymentService is kept
 * separate from PaymentService: this is an admin-only drill-down concern,
 * not part of the core movie CRUD surface MovieService owns. MovieService
 * still owns *writing* history (see its create/changeStatus) since that has
 * to happen atomically with the movie mutation itself.
 */
@Service
public class MovieStatusHistoryService {

    private final MovieRepository movieRepository;
    private final MovieStatusHistoryRepository movieStatusHistoryRepository;
    private final MovieStatusHistoryMapper movieStatusHistoryMapper;

    public MovieStatusHistoryService(
            MovieRepository movieRepository,
            MovieStatusHistoryRepository movieStatusHistoryRepository,
            MovieStatusHistoryMapper movieStatusHistoryMapper) {
        this.movieRepository = movieRepository;
        this.movieStatusHistoryRepository = movieStatusHistoryRepository;
        this.movieStatusHistoryMapper = movieStatusHistoryMapper;
    }

    @Transactional(readOnly = true)
    public List<MovieStatusHistoryEntryResponse> getHistory(UUID movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found");
        }
        return movieStatusHistoryMapper.toResponseList(
                movieStatusHistoryRepository.findByMovieIdOrderByChangedAtDesc(movieId));
    }
}
