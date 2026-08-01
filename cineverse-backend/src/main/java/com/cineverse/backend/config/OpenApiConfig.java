package com.cineverse.backend.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the accessToken returned by /api/v1/auth/login or /refresh")
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cineverseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CineVerse API")
                        .description("CineVerse 电影院订票系统 - 后端 API")
                        .version("v0.1"));
    }
}
