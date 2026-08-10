package com.cineverse.backend.user.dto;

import com.cineverse.backend.user.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Role is required")
        @Schema(example = "ADMIN")
        Role role) {
}
