package com.cineverse.backend.movie.repository;

import com.cineverse.backend.movie.entity.Genre;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, UUID> {
}
