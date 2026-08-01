package com.cineverse.backend.cinema.service;

import com.cineverse.backend.cinema.dto.CreateHallRequest;
import com.cineverse.backend.cinema.dto.HallResponse;
import com.cineverse.backend.cinema.dto.HallSeatsResponse;
import com.cineverse.backend.cinema.entity.Cinema;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.cinema.mapper.HallMapper;
import com.cineverse.backend.cinema.mapper.SeatMapper;
import com.cineverse.backend.cinema.repository.CinemaRepository;
import com.cineverse.backend.cinema.repository.HallRepository;
import com.cineverse.backend.cinema.repository.SeatRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HallService {

    private final CinemaRepository cinemaRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final SeatLayoutGenerator seatLayoutGenerator;
    private final HallMapper hallMapper;
    private final SeatMapper seatMapper;

    public HallService(
            CinemaRepository cinemaRepository,
            HallRepository hallRepository,
            SeatRepository seatRepository,
            SeatLayoutGenerator seatLayoutGenerator,
            HallMapper hallMapper,
            SeatMapper seatMapper) {
        this.cinemaRepository = cinemaRepository;
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
        this.seatLayoutGenerator = seatLayoutGenerator;
        this.hallMapper = hallMapper;
        this.seatMapper = seatMapper;
    }

    @Transactional(readOnly = true)
    public List<HallResponse> listByCinema(UUID cinemaId) {
        requireCinema(cinemaId);
        return hallMapper.toResponseList(hallRepository.findByCinemaIdOrderByName(cinemaId));
    }

    /**
     * Creates the hall and, in the same transaction, generates its full
     * seat layout — last row is COUPLE (paired every 2 columns), every
     * other row STANDARD. There is no separate "generate seats" step or
     * endpoint; a hall is never left without seats.
     */
    @Transactional
    public HallResponse createHall(UUID cinemaId, CreateHallRequest request) {
        Cinema cinema = requireCinema(cinemaId);

        Hall newHall = new Hall(cinema, request.name(), request.totalRows(), request.totalColumns());
        // saveAndFlush: same reasoning as CinemaService.create — the response
        // must carry the DB-generated createdAt/updatedAt, not null.
        Hall hall = hallRepository.saveAndFlush(newHall);

        List<SeatLayoutGenerator.SeatSpec> specs = seatLayoutGenerator.generate(
                request.totalRows(), request.totalColumns(), Set.of(request.totalRows()));
        List<Seat> seats = specs.stream()
                .map(spec -> new Seat(hall, spec.rowLabel(), spec.columnNumber(), spec.seatType()))
                .toList();
        seatRepository.saveAll(seats);

        return hallMapper.toResponse(hall);
    }

    @Transactional(readOnly = true)
    public HallSeatsResponse getHallSeats(UUID hallId) {
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        List<Seat> seats = seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(hallId);
        return new HallSeatsResponse(
                hall.getId(), hall.getName(), hall.getTotalRows(), hall.getTotalColumns(),
                seatMapper.toResponseList(seats));
    }

    private Cinema requireCinema(UUID cinemaId) {
        return cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cinema not found"));
    }
}
