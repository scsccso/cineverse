package com.cineverse.backend.movie.controller;

import com.cineverse.backend.movie.dto.GenreResponse;
import com.cineverse.backend.movie.mapper.GenreMapper;
import com.cineverse.backend.movie.repository.GenreRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/genres")
@Tag(name = "Genres", description = "只读:可选的电影分类列表(无 CRUD API,种子数据固定维护)")
public class GenreController {

    private final GenreRepository genreRepository;
    private final GenreMapper genreMapper;

    public GenreController(GenreRepository genreRepository, GenreMapper genreMapper) {
        this.genreRepository = genreRepository;
        this.genreMapper = genreMapper;
    }

    @GetMapping
    @Operation(summary = "获取全部分类", description = "公开接口,无需登录")
    public List<GenreResponse> list() {
        return genreMapper.toResponseList(genreRepository.findAll(Sort.by("name")));
    }
}
