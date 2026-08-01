package com.cineverse.backend.config;

import com.cineverse.backend.storage.StorageProperties;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves locally-stored uploads (posters/backdrops) back out over HTTP. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    public WebMvcConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(storageProperties.uploadDir()).toUri().toString();
        registry.addResourceHandler(storageProperties.baseUrl() + "/**")
                .addResourceLocations(location);
    }
}
