package com.cineverse.backend.user.mapper;

import com.cineverse.backend.user.dto.UserResponse;
import com.cineverse.backend.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
