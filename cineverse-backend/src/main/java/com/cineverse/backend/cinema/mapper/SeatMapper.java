package com.cineverse.backend.cinema.mapper;

import com.cineverse.backend.cinema.dto.SeatResponse;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.cinema.entity.SeatType;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SeatMapper {

    @Mapping(target = "columnSpan", expression = "java(columnSpanFor(seat.getSeatType()))")
    SeatResponse toResponse(Seat seat);

    List<SeatResponse> toResponseList(List<Seat> seats);

    default Integer columnSpanFor(SeatType seatType) {
        return seatType == SeatType.COUPLE ? 2 : 1;
    }
}
