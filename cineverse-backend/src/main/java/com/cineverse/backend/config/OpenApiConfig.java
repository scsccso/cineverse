package com.cineverse.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
