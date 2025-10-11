package ru.oparin.troyka.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Configuration
public class WebConfig {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Bean
    public RouterFunction<ServerResponse> staticResourceRouter() {
        return RouterFunctions
                .route(GET("/files/**"), request -> {
                    String path = request.pathVariable("**");
                    Path filePath = Paths.get(uploadDir).resolve(path);
                    
                    try {
                        Resource resource = new org.springframework.core.io.FileSystemResource(filePath.toFile());
                        
                        if (resource.exists() && resource.isReadable()) {
                            // Определяем Content-Type по расширению файла
                            String contentType = getContentType(path);
                            
                            return ok()
                                    .contentType(MediaType.parseMediaType(contentType))
                                    .bodyValue(resource);
                        } else {
                            return ServerResponse.notFound().build();
                        }
                    } catch (Exception e) {
                        return ServerResponse.status(500).build();
                    }
                });
    }

    private String getContentType(String filename) {
        String extension = filename.toLowerCase();
        if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (extension.endsWith(".png")) {
            return "image/png";
        } else if (extension.endsWith(".gif")) {
            return "image/gif";
        } else if (extension.endsWith(".webp")) {
            return "image/webp";
        } else if (extension.endsWith(".svg")) {
            return "image/svg+xml";
        } else {
            return "application/octet-stream";
        }
    }
}
