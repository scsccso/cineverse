package com.cineverse.backend.movie.mapper;

import com.cineverse.backend.movie.dto.MovieStatusHistoryEntryResponse;
import com.cineverse.backend.movie.entity.MovieStatusHistory;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** A flat, single-hop field copy (history -> changedBy.email) — the case
 * MapStruct is the right tool for (see AdminPaymentMapper's doc comment on
 * why a much deeper, five-hop assembly went imperative instead). MapStruct
 * generates a null check for the nested "changedBy.email" source path on
 * its own, matching changedBy being ON DELETE SET NULL. */
@Mapper(componentModel = "spring")
public interface MovieStatusHistoryMapper {

    @Mapping(target = "changedByEmail", source = "changedBy.email")
    MovieStatusHistoryEntryResponse toResponse(MovieStatusHistory history);

    List<MovieStatusHistoryEntryResponse> toResponseList(List<MovieStatusHistory> history);
}
