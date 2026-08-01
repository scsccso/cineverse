package com.cineverse.backend.movie.mapper;

import com.cineverse.backend.movie.dto.GenreResponse;
import com.cineverse.backend.movie.entity.Genre;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GenreMapper {

    GenreResponse toResponse(Genre genre);

    List<GenreResponse> toResponseList(List<Genre> genres);
}
