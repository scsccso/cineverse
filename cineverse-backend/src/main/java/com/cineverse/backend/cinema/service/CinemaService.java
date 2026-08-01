package com.cineverse.backend.cinema.service;

import com.cineverse.backend.cinema.dto.CinemaResponse;
import com.cineverse.backend.cinema.dto.CreateCinemaRequest;
import com.cineverse.backend.cinema.entity.Cinema;
import com.cineverse.backend.cinema.mapper.CinemaMapper;
import com.cineverse.backend.cinema.repository.CinemaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CinemaService {

    private final CinemaRepository cinemaRepository;
    private final CinemaMapper cinemaMapper;

    public CinemaService(CinemaRepository cinemaRepository, CinemaMapper cinemaMapper) {
        this.cinemaRepository = cinemaRepository;
        this.cinemaMapper = cinemaMapper;
    }

    @Transactional(readOnly = true)
    public List<CinemaResponse> list() {
        return cinemaMapper.toResponseList(cinemaRepository.findAll());
    }

    @Transactional
    public CinemaResponse create(CreateCinemaRequest request) {
        Cinema cinema = new Cinema(request.name(), request.address());
        // saveAndFlush (not save): createdAt/updatedAt are populated by
        // @CreationTimestamp/@UpdateTimestamp at flush time, and the
        // response must reflect them, not null.
        return cinemaMapper.toResponse(cinemaRepository.saveAndFlush(cinema));
    }
}
