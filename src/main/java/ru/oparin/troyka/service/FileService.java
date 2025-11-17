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
                        String fileUrl = "https://" + serverHost + "/files/" + uniqueFilename;
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

    public Mono<String> saveAvatarAndGetUrl(FilePart filePart, String username) {
        log.info("Пользователь {} загружает аватар: оригинальное имя={}", username, filePart.filename());

        try {
            // Создаем директорию для загрузки аватаров, если она не существует
            Path uploadPath = Paths.get(uploadDir).resolve("avatar");
            log.info("Проверяем существование директории для загрузки аватаров: {}", uploadPath.toAbsolutePath());

            if (!Files.exists(uploadPath)) {
                log.info("Создаем директорию для загрузки аватаров: {}", uploadPath.toAbsolutePath());
                Files.createDirectories(uploadPath);
            }

            // Проверяем права на запись
            if (!Files.isWritable(uploadPath)) {
                log.error("Директория {} недоступна для записи", uploadPath.toAbsolutePath());
                return Mono.error(new RuntimeException("Директория загрузки аватаров недоступна для записи"));
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
            log.info("Сохраняем аватар по пути: {}", filePath.toAbsolutePath());

            return filePart.transferTo(filePath)
                    .then(Mono.fromCallable(() -> {
                        // Возвращаем URL аватара
                        String fileUrl = "https://" + serverHost + "/files/avatar/" + uniqueFilename;
                        log.info("Аватар успешно загружен пользователем {}: {}", username, fileUrl);
                        return fileUrl;
                    }))
                    .onErrorResume(AccessDeniedException.class, e -> {
                        log.error("Ошибка доступа при сохранении аватара пользователем {}. Путь: {}", username, filePath.toAbsolutePath(), e);
                        return Mono.error(new RuntimeException("Нет прав доступа для сохранения аватара. Проверьте права на директорию: " + uploadDir + "/avatar"));
                    })
                    .onErrorResume(Exception.class, e -> {
                        log.error("Ошибка при сохранении аватара пользователем {}. Путь: {}", username, filePath.toAbsolutePath(), e);
                        return Mono.error(new RuntimeException("Ошибка при сохранении аватара: " + e.getMessage()));
                    });

        } catch (Exception e) {
            log.error("Ошибка при обработке загрузки аватара пользователем {}", username, e);
            return Mono.error(new RuntimeException("Ошибка при обработке аватара: " + e.getMessage()));
        }
    }

    public Mono<String> saveAvatar(FilePart filePart) {
        return userService.getCurrentUser()
                .flatMap(userInfoDTO -> userService.findByUsernameOrThrow(userInfoDTO.getUsername()))
                .flatMap(user -> {
                    // Получаем старый аватар перед загрузкой нового
                    return userAvatarService.getUserAvatarByUserId(user.getId())
                            .map(UserAvatar::getAvatarUrl)
                            .defaultIfEmpty("")
                            .flatMap(oldAvatarUrl ->
                                    saveAvatarAndGetUrl(filePart, user.getUsername())
                                            .flatMap(newFileUrl ->
                                                    userAvatarService.saveUserAvatar(user.getId(), newFileUrl)
                                                            .then(deleteOldAvatarFile(oldAvatarUrl))
                                                            .then(Mono.just(newFileUrl))
                                            )
                            );
                });
    }

    public Mono<String> getCurrentUserAvatar() {
        return userService.getCurrentUser()
                .flatMap(userInfoDTO -> userService.findByUsernameOrThrow(userInfoDTO.getUsername()))
                .flatMap(user -> userAvatarService.getUserAvatarByUserId(user.getId()))
                .map(UserAvatar::getAvatarUrl);
    }

    private Mono<Void> deleteOldAvatarFile(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            try {
                // Извлекаем имя файла из URL
                // URL формат: https://24reshai.ru/files/avatar/filename.jpg
                String filename = avatarUrl.substring(avatarUrl.lastIndexOf("/") + 1);

                // Проверяем, что файл находится в папке avatar для безопасности
                Path avatarDir = Paths.get(uploadDir).resolve("avatar");
                Path filePath = avatarDir.resolve(filename);

                // Дополнительная проверка безопасности - файл должен быть внутри avatar директории
                if (!filePath.normalize().startsWith(avatarDir.normalize())) {
                    log.warn("Попытка удалить файл вне директории аватаров: {}", filePath);
                    return null;
                }

                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.info("Старый аватар удален: {}", filePath);
                } else {
                    log.info("Старый аватар не найден для удаления: {}", filePath);
                }

                return null;
            } catch (Exception e) {
                log.error("Ошибка при удалении старого аватара: {}", avatarUrl, e);
                // Не прерываем выполнение, если не удалось удалить старый файл
                return null;
            }
        });
    }

    public Mono<Void> deleteCurrentUserAvatar() {
        return userService.getCurrentUser()
                .flatMap(userInfoDTO -> userService.findByUsernameOrThrow(userInfoDTO.getUsername()))
                .flatMap(user ->
                        // Сначала получаем URL аватара
                        userAvatarService.getUserAvatarByUserId(user.getId())
                                .map(UserAvatar::getAvatarUrl)
                                .cast(String.class)
                                .defaultIfEmpty("")
                                .flatMap(avatarUrl ->
                                        // Удаляем физический файл
                                        deleteOldAvatarFile(avatarUrl)
                                                .then(
                                                        // Затем удаляем запись из БД
                                                        userAvatarService.deleteUserAvatarByUserId(user.getId())
                                                )
                                )
                );
    }

    public Mono<ResponseEntity<Resource>> getFile(String filename) {
        return Mono.fromCallable(() -> {
            try {
                // Нормализуем путь для Windows
                String normalizedUploadDir = uploadDir.replace("\\", "/");
                if (!normalizedUploadDir.endsWith("/")) {
                    normalizedUploadDir += "/";
                }

                log.debug("Запрос файла: {}", filename);
                Path file = Paths.get(normalizedUploadDir).resolve(filename);
                Resource resource = new UrlResource(file.toUri());

                if (resource.exists() && resource.isReadable()) {
                    // Логируем размер файла для диагностики
                    try {
                        long fileSize = Files.size(file);
                        log.debug("Размер файла {}: {} байт ({} МБ)", filename, fileSize, fileSize / (1024.0 * 1024.0));
                    } catch (IOException e) {
                        log.warn("Не удалось получить размер файла: {}", filename);
                    }

                    String contentType;
                    try {
                        contentType = Files.probeContentType(file);
                    } catch (IOException e) {
                        log.warn("Не удалось определить тип контента (хэдер) для файла: {}", filename);
                        contentType = "application/octet-stream";
                    }

                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                            // Добавляем заголовки кеширования для ускорения повторных запросов
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600") // Кешируем на 1 час
                            .header(HttpHeaders.ETAG, "\"" + filename + "-" + Files.getLastModifiedTime(file).toMillis() + "\"")
                            .body(resource);
                } else {
                    log.warn("Файл {} не найден или недоступен для чтения", filename);
                    return ResponseEntity.notFound().build();
                }
            } catch (Exception e) {
                log.error("Ошибка при попытке получить файл: {}", filename, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        });
    }

    public Mono<ResponseEntity<Resource>> getExampleFile(String filename) {
        return Mono.fromCallable(() -> {
            try {
                // Создаем путь к файлу в папке examples
                String normalizedUploadDir = uploadDir.replace("\\", "/");
                if (!normalizedUploadDir.endsWith("/")) {
                    normalizedUploadDir += "/";
                }

                Path file = Paths.get(normalizedUploadDir, "examples", filename);
                Resource resource = new UrlResource(file.toUri());

                if (resource.exists() && resource.isReadable()) {
                    String contentType = getContentType(filename, file);
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000") // Кешируем на год
                            .body(resource);
                } else {
                    log.warn("Файл примера {} не найден или недоступен для чтения", filename);
                    return ResponseEntity.notFound().build();
                }
            } catch (Exception e) {
                log.error("Ошибка при попытке получить файл примера: {}", filename, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        });
    }

    private String getContentType(String filename, Path file) {
        String contentType;
        try {
            contentType = Files.probeContentType(file);
            if (contentType == null) {
                // Определяем тип по расширению файла
                String fileExtension = filename.toLowerCase();
                if (fileExtension.endsWith(".jpg") || fileExtension.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (fileExtension.endsWith(".png")) {
                    contentType = "image/png";
                } else if (fileExtension.endsWith(".gif")) {
                    contentType = "image/gif";
                } else if (fileExtension.endsWith(".webp")) {
                    contentType = "image/webp";
                } else {
                    contentType = "application/octet-stream";
                }
            }
        } catch (IOException e) {
            log.warn("Не удалось определить тип контента для файла: {}", filename);
            contentType = "image/jpeg"; // По умолчанию для изображений
        }
        return contentType;
    }
}