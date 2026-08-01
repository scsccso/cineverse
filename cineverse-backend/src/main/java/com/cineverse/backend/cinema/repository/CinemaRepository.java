package com.cineverse.backend.cinema.repository;

import com.cineverse.backend.cinema.entity.Cinema;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinemaRepository extends JpaRepository<Cinema, UUID> {
}
