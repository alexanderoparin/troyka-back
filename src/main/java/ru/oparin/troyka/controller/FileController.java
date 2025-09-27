package ru.oparin.troyka.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.oparin.troyka.util.SecurityUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/files")
public class FileController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PostMapping("/upload")
    public Mono<ResponseEntity<String>> uploadFile(@RequestParam("file") MultipartFile file) {
        return SecurityUtil.getCurrentUsername()
                .doOnNext(username -> log.info("Получен запрос на загрузку файла от пользователя: {}", username))
                .publishOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.boundedElastic())
                .flatMap(username -> {
                    try {
                        // Создаем директорию для загрузки, если она не существует
                        Path uploadPath = Paths.get(uploadDir);
                        if (!Files.exists(uploadPath)) {
                            Files.createDirectories(uploadPath);
                        }

                        // Генерируем уникальное имя файла
                        String originalFilename = file.getOriginalFilename();
                        String fileExtension = "";
                        if (originalFilename != null && originalFilename.contains(".")) {
                            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                        }
                        String uniqueFilename = UUID.randomUUID() + fileExtension;

                        // Сохраняем файл
                        Path filePath = uploadPath.resolve(uniqueFilename);
                        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                        // Возвращаем URL файла
                        String fileUrl = "/files/" + uniqueFilename;
                        log.info("Файл успешно загружен: {}", fileUrl);
                        return Mono.just(ResponseEntity.ok(fileUrl));
                    } catch (IOException e) {
                        log.error("Ошибка при загрузке файла", e);
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Ошибка при загрузке файла"));
                    }
                });
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path file = Paths.get(uploadDir).resolve(filename);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType;
                try {
                    contentType = Files.probeContentType(file);
                } catch (IOException e) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}