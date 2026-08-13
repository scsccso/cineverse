package com.cineverse.backend.movie.controller;

import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.dto.MovieResponse;
import com.cineverse.backend.movie.dto.UpdateMovieImageUrlsRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.movie.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/movies")
@Tag(name = "Movies", description = "浏览公开,只读;新增/修改/删除/图片上传仅 ADMIN")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    @Operation(summary = "分页查询电影", description = "公开接口,无需登录;支持按 status、genre 筛选")
    public Page<MovieResponse> list(
            @RequestParam(required = false) MovieStatus status,
            @RequestParam(required = false) UUID genre,
            @PageableDefault(size = 20) Pageable pageable) {
        return movieService.list(status, genre, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取电影详情", description = "公开接口,无需登录")
    public MovieResponse getById(@PathVariable UUID id) {
        return movieService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "创建电影", description = "仅 ADMIN")
    public MovieResponse create(@Valid @RequestBody MovieRequest request) {
        return movieService.create(request);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "更新电影", description = "仅 ADMIN;全量替换")
    public MovieResponse update(@PathVariable UUID id, @Valid @RequestBody MovieRequest request) {
        return movieService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "删除电影", description = "仅 ADMIN;同时清理已上传的海报/背景图")
    public void delete(@PathVariable UUID id) {
        movieService.delete(id);
    }

    @PostMapping(path = "/{id}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "上传海报", description = "仅 ADMIN;jpg/png/webp,单文件不超过 5MB")
    public MovieResponse uploadPoster(
            @PathVariable UUID id,
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        return movieService.updatePoster(id, file);
    }

    @PostMapping(path = "/{id}/backdrop", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "上传背景图", description = "仅 ADMIN;jpg/png/webp,单文件不超过 5MB")
    public MovieResponse uploadBackdrop(
            @PathVariable UUID id,
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        return movieService.updateBackdrop(id, file);
    }

    @PatchMapping("/{id}/image-urls")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "直接设置海报/背景图为外部 URL(热链,不上传文件)",
            description = "仅 ADMIN;局部更新——只设置请求体里非空的字段,另一个字段留空则保持不变。"
                    + "用于 TMDB 搜索预填创建流程,不经过 StorageService")
    public MovieResponse updateImageUrls(
            @PathVariable UUID id, @Valid @RequestBody UpdateMovieImageUrlsRequest request) {
        return movieService.updateImageUrls(id, request);
    }
}
