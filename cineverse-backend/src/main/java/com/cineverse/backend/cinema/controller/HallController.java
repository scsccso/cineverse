package com.cineverse.backend.cinema.controller;

import com.cineverse.backend.cinema.dto.HallSeatsResponse;
import com.cineverse.backend.cinema.service.HallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/halls")
@Tag(name = "Halls", description = "浏览公开,只读")
public class HallController {

    private final HallService hallService;

    public HallController(HallService hallService) {
        this.hallService = hallService;
    }

    @GetMapping("/{id}/seats")
    @Operation(summary = "获取影厅完整座位布局",
            description = "公开接口,无需登录。每个座位含 row/column/columnSpan/type,"
                    + "是 Phase 5 座位图渲染的基础数据格式")
    public HallSeatsResponse getSeats(@PathVariable UUID id) {
        return hallService.getHallSeats(id);
    }
}
