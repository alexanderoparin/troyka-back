package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.UserAvatar;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.host}")
    private String serverHost;

    @Value("${server.port:8080}")
    private String serverPort;

    private final UserService userService;
    private final UserAvatarService userAvatarService;

    public FileService(UserService userService, UserAvatarService userAvatarService) {
        this.userService = userService;
        this.userAvatarService = userAvatarService;
    }

    public Mono<ResponseEntity<String>> saveFile(FilePart filePart, String username) {
        return saveFileAndGetUrl(filePart, username)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body("Ошибка при загрузке файла: " + e.getMessage())));
    }

    public Mono<String> saveFileAndGetUrl(FilePart filePart, String username) {
        log.info("Пользователь {} загружает файл: оригинальное имя={}", username, filePart.filename());

        try {
            // Создаем директорию для загрузки, если она не существует
            Path uploadPath = Paths.get(uploadDir);
            log.info("Проверяем существование директории для загрузки: {}", uploadPath.toAbsolutePath());

            if (!Files.exists(uploadPath)) {
                log.info("Создаем директорию для загрузки: {}", uploadPath.toAbsolutePath());
                Files.createDirectories(uploadPath);
            }

            // Проверяем права на запись
            if (!Files.isWritable(uploadPath)) {
                log.error("Директория {} недоступна для записи", uploadPath.toAbsolutePath());
                return Mono.error(new RuntimeException("Директория загрузки недоступна для записи"));
            }

            // Генерируем уникальное имя файла
            String originalFilename = filePart.filename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            // Сохраняем файл
            Path filePath = uploadPath.resolve(uniqueFilename);
            log.info("Сохраняем файл по пути: {}", filePath.toAbsolutePath());

            return filePart.transferTo(filePath)
                    .then(Mono.fromCallable(() -> {
                        // Возвращаем URL файла, который может использовать FAL AI
                        String fileUrl = "http://" + serverHost + ":" + serverPort + "/files/" + uniqueFilename;
                        log.info("Файл успешно загружен пользователем {}: {}", username, fileUrl);
                        return fileUrl;
                    }))
                    .onErrorResume(AccessDeniedException.class, e -> {
                        log.error("Ошибка доступа при сохранении файла пользователем {}. Путь: {}", username, filePath.toAbsolutePath(), e);
                        return Mono.error(new RuntimeException("Нет правдоступа для сохранения файла. Проверьте права на директорию: " + uploadDir));
                    })
                    .onErrorResume(Exception.class, e -> {
                        log.error("Ошибка при сохранении файла пользователем {}. Путь: {}", username, filePath.toAbsolutePath(), e);
                        return Mono.error(new RuntimeException("Ошибкапри сохранении файла: " + e.getMessage()));
                    });

        } catch (Exception e) {
            log.error("Ошибка при обработке загрузки файла пользователем {}", username, e);
            return Mono.error(new RuntimeException("Ошибка при обработке файла: " + e.getMessage()));
        }
    }

    public Mono<String> saveAvatar(FilePart filePart) {
        return userService.getCurrentUser()
                .flatMap(userInfoDTO -> userService.findByUsernameOrThrow(userInfoDTO.getUsername()))
                .flatMap(user -> saveFileAndGetUrl(filePart, user.getUsername())
                        .flatMap(fileUrl -> userAvatarService.saveUserAvatar(user.getId(), fileUrl)
                                .then(Mono.just(fileUrl))));
    }

    public Mono<String> getCurrentUserAvatar() {
        return userService.getCurrentUser()
                .flatMap(userInfoDTO -> userService.findByUsernameOrThrow(userInfoDTO.getUsername()))
                .flatMap(user -> userAvatarService.getUserAvatarByUserId(user.getId()))
                .map(UserAvatar::getAvatarUrl);
    }

    public Mono<Void> deleteCurrentUserAvatar() {
        return userService.getCurrentUser()
                .flatMap(userInfoDTO -> userService.findByUsernameOrThrow(userInfoDTO.getUsername()))
                .flatMap(user -> userAvatarService.deleteUserAvatarByUserId(user.getId()));
    }

    public Mono<ResponseEntity<Resource>> getFile(String filename) {
        return Mono.fromCallable(() -> {
            try {
                // Нормализуем путь для Windows
                String normalizedUploadDir = uploadDir.replace("\\", "/");
                if (!normalizedUploadDir.endsWith("/")) {
                    normalizedUploadDir += "/";
                }

                log.info("Запрос файла: {}", filename);
                Path file = Paths.get(normalizedUploadDir).resolve(filename);
                Resource resource = new UrlResource(file.toUri());

                if (resource.exists() && resource.isReadable()) {
                    String contentType;
                    try {
                        contentType = Files.probeContentType(file);
                    } catch (IOException e) {
                        log.warn("Не удалось определить тип контента для файла: {}", filename);
                        contentType = "application/octet-stream";
                    }

                    log.info("Файл {} найден, тип контента: {}", filename, contentType);
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                            .body(resource);
                } else {
                    log.warn("Файл {} не найден или недоступен для чтения", filename);
                    return ResponseEntity.notFound().build();
                }
            } catch (Exception e) {
                log.error("Ошибка при попытке получить файл: " + filename, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        });
    }
}