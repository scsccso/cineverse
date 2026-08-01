package com.cineverse.backend.movie.mapper;

import com.cineverse.backend.movie.dto.MovieResponse;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.storage.StorageProperties;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Abstract class (not interface) so MapStruct's Spring component model can
 * field-inject StorageProperties — needed to resolve poster/backdrop
 * placeholder URLs, which callers must never see as null.
 */
@Mapper(componentModel = "spring", uses = GenreMapper.class)
public abstract class MovieMapper {

    @Autowired
    protected StorageProperties storageProperties;

    @Mapping(target = "posterUrl", expression = "java(resolveUrl(movie.getPosterUrl(), storageProperties.defaultPosterUrl()))")
    @Mapping(target = "backdropUrl", expression = "java(resolveUrl(movie.getBackdropUrl(), storageProperties.defaultBackdropUrl()))")
    public abstract MovieResponse toResponse(Movie movie);

    protected String resolveUrl(String url, String fallback) {
        return (url != null && !url.isBlank()) ? url : fallback;
    }
}
