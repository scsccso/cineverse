package com.cineverse.backend.cinema.controller;

import com.cineverse.backend.cinema.dto.CinemaResponse;
import com.cineverse.backend.cinema.dto.CreateCinemaRequest;
import com.cineverse.backend.cinema.dto.CreateHallRequest;
import com.cineverse.backend.cinema.dto.HallResponse;
import com.cineverse.backend.cinema.service.CinemaService;
import com.cineverse.backend.cinema.service.HallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cinemas")
@Tag(name = "Cinemas", description = "浏览公开,只读;新增仅 ADMIN")
public class CinemaController {

    private final CinemaService cinemaService;
    private final HallService hallService;

    public CinemaController(CinemaService cinemaService, HallService hallService) {
        this.cinemaService = cinemaService;
        this.hallService = hallService;
    }

    @GetMapping
    @Operation(summary = "获取全部分店", description = "公开接口,无需登录")
    public List<CinemaResponse> list() {
        return cinemaService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "创建分店", description = "仅 ADMIN")
    public CinemaResponse create(@Valid @RequestBody CreateCinemaRequest request) {
        return cinemaService.create(request);
    }

    @GetMapping("/{cinemaId}/halls")
    @Operation(summary = "获取分店下的影厅列表", description = "公开接口,无需登录")
    public List<HallResponse> listHalls(@PathVariable UUID cinemaId) {
        return hallService.listByCinema(cinemaId);
    }

    @PostMapping("/{cinemaId}/halls")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "创建影厅", description = "仅 ADMIN;根据 totalRows/totalColumns 自动生成座位布局"
            + "(最后一排为情侣座,其余为标准座),没有单独的生成座位接口")
    public HallResponse createHall(@PathVariable UUID cinemaId, @Valid @RequestBody CreateHallRequest request) {
        return hallService.createHall(cinemaId, request);
    }
}
