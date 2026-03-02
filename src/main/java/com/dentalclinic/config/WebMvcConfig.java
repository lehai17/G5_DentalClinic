package com.dentalclinic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Expose directory 'uploads/avatars' so images can be accessed via URL
        // '/uploads/avatars/...'
        Path avatarUploadDir = Paths.get("./uploads/avatars");
        String avatarUploadPath = avatarUploadDir.toFile().getAbsolutePath();

        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations("file:/" + avatarUploadPath + "/");
    }
}
