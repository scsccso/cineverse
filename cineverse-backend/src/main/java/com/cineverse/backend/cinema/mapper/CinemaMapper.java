package com.cineverse.backend.cinema.mapper;

import com.cineverse.backend.cinema.dto.CinemaResponse;
import com.cineverse.backend.cinema.entity.Cinema;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CinemaMapper {

    CinemaResponse toResponse(Cinema cinema);

    List<CinemaResponse> toResponseList(List<Cinema> cinemas);
}
