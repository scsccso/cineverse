package com.cineverse.backend.user.controller;

import com.cineverse.backend.user.dto.UpdateUserRoleRequest;
import com.cineverse.backend.user.dto.UserResponse;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.mapper.UserMapper;
import com.cineverse.backend.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserMapper userMapper;

    public AdminUserController(AdminUserService adminUserService, UserMapper userMapper) {
        this.adminUserService = adminUserService;
        this.userMapper = userMapper;
    }

    @GetMapping
    @Operation(summary = "获取用户列表", description = "分页查询所有用户(仅限 ADMIN),"
            + "可选按 email 模糊搜索(大小写不敏感)")
    public Page<UserResponse> getAllUsers(@RequestParam(required = false) String email, Pageable pageable) {
        Page<User> users = adminUserService.getAllUsers(email, pageable);
        return users.map(userMapper::toResponse);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "修改用户角色", description = "修改指定用户的角色(仅限 ADMIN,不能修改自己的角色)")
    @ApiResponse(responseCode = "409", description = "不能修改自己的角色")
    public UserResponse updateUserRole(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRoleRequest request, Authentication authentication) {
        User user = adminUserService.updateUserRole(id, request, UUID.fromString(authentication.getName()));
        return userMapper.toResponse(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除用户", description = "删除指定用户。如果用户有订单记录或是调用者自己则返回409(仅限 ADMIN)")
    @ApiResponse(responseCode = "204", description = "删除成功")
    @ApiResponse(responseCode = "409", description = "用户有订单记录,或是调用者自己的账号,无法删除")
    public void deleteUser(@PathVariable UUID id, Authentication authentication) {
        adminUserService.deleteUser(id, UUID.fromString(authentication.getName()));
    }
}
