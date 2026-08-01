package com.cineverse.backend.cinema.mapper;

import com.cineverse.backend.cinema.dto.HallResponse;
import com.cineverse.backend.cinema.entity.Hall;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HallMapper {

    @Mapping(target = "cinemaId", source = "cinema.id")
    HallResponse toResponse(Hall hall);

    List<HallResponse> toResponseList(List<Hall> halls);
}
