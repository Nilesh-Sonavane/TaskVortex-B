package com.taskvortex.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Define the base path just like in the service
        Path baseDir = Paths.get("")
                .toAbsolutePath()
                .getParent()
                .getParent()
                .resolve("taskvortex-data")
                .normalize();

        // 1. Map /profiles/** to the profiles folder
        registry.addResourceHandler("/profiles/**")
                .addResourceLocations("file:" + baseDir.resolve("profiles").toString() + "/");

        // 2. Map /attachments/** to the attachments folder
        registry.addResourceHandler("/attachments/**")
                .addResourceLocations("file:" + baseDir.resolve("attachments").toString() + "/");
    }
}