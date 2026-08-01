package com.cineverse.backend.cinema.repository;

import com.cineverse.backend.cinema.entity.Hall;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HallRepository extends JpaRepository<Hall, UUID> {

    List<Hall> findByCinemaIdOrderByName(UUID cinemaId);
}
