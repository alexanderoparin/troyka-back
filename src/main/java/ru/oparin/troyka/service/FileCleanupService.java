package ru.oparin.troyka.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Сервис для очистки старых неиспользуемых файлов с диска.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCleanupService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.host}")
    private String serverHost;

    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;

    /**
     * Очистить старые неиспользуемые файлы.
     *
     * @param daysOld минимальный возраст файла в днях для удаления
     * @return количество удаленных файлов
     */
    public Mono<Integer> cleanupOldUnusedFiles(int daysOld) {
        return getAllUsedFileNames()
                .collectList()
                .flatMap(usedFileNamesList -> {
                    Set<String> usedFileNames = new HashSet<>(usedFileNamesList);
                    return Mono.fromCallable(() -> deleteUnusedFiles(usedFileNames, daysOld))
                            .onErrorResume(e -> {
                                log.error("Ошибка при очистке файлов", e);
                                return Mono.just(0);
                            });
                });
    }

    private Flux<String> getAllUsedFileNames() {
        return imageGenerationHistoryRepository.findAll()
                .flatMap(history -> {
                    Set<String> files = new HashSet<>();

                    List<String> inputImageUrls = history.getInputImageUrls();
                    if (inputImageUrls != null) {
                        inputImageUrls.forEach(url -> extractFileName(url).ifPresent(files::add));
                    }

                    return Flux.fromIterable(files);
                })
                .distinct();
    }

    private Optional<String> extractFileName(String url) {
        if (url == null || url.isEmpty()) {
            return Optional.empty();
        }

        try {
            String baseUrl = "https://" + serverHost + "/files/";
            if (url.startsWith(baseUrl)) {
                return Optional.of(url.substring(baseUrl.length()));
            }
        } catch (Exception e) {
            log.warn("Не удалось извлечь имя файла из URL: {}", url);
        }

        return Optional.empty();
    }

    private int deleteUnusedFiles(Set<String> usedFileNames, int daysOld) {
        AtomicInteger deletedCount = new AtomicInteger(0);
        long cutoffTime = Instant.now().minus(Duration.ofDays(daysOld)).toEpochMilli();

        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                log.warn("Директория загрузки не существует: {}", uploadPath);
                return 0;
            }

            processDirectory(uploadPath, uploadPath, usedFileNames, cutoffTime, deletedCount);
        } catch (Exception e) {
            log.error("Ошибка при удалении неиспользуемых файлов", e);
        }

        return deletedCount.get();
    }

    private void processDirectory(Path rootPath, Path directory, Set<String> usedFileNames,
                                 long cutoffTime, AtomicInteger deletedCount) {
        try (Stream<Path> paths = Files.list(directory)) {
            paths.forEach(path -> {
                try {
                    if (Files.isRegularFile(path)) {
                        Path relativePath = rootPath.relativize(path);
                        String filePath = relativePath.toString().replace("\\", "/");

                        if (usedFileNames.contains(filePath)) {
                            return;
                        }

                        long lastModified = Files.getLastModifiedTime(path).toMillis();

                        if (lastModified < cutoffTime) {
                            long fileSize = Files.size(path);
                            long ageInDays = Duration.ofMillis(Instant.now().toEpochMilli() - lastModified).toDays();
                            Files.delete(path);
                            deletedCount.incrementAndGet();
                            log.info("Удален старый неиспользуемый файл: {} (размер: {} байт, возраст: {} дней)",
                                    filePath, fileSize, ageInDays);
                        }
                    }
                } catch (IOException e) {
                    log.warn("Не удалось обработать файл: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.error("Ошибка при чтении директории: {}", directory, e);
        }
    }

    /**
     * Получить информацию о файле.
     *
     * @param filename имя файла
     * @return информация о файле или пустой Mono, если файл не найден
     */
    public Mono<FileInfo> getFileInfo(String filename) {
        return Mono.fromCallable(() -> {
            Path file = Paths.get(uploadDir).resolve(filename);

            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                return null;
            }

            long fileSize = Files.size(file);
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            LocalDateTime lastModifiedDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(lastModified),
                    ZoneId.systemDefault()
            );

            LocalDateTime createdDateTime = null;
            try {
                Object creationTime = Files.getAttribute(file, "creationTime");
                if (creationTime != null) {
                    long createdMillis = ((FileTime) creationTime).toMillis();
                    createdDateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(createdMillis),
                            ZoneId.systemDefault()
                    );
                }
            } catch (Exception e) {
                log.debug("Не удалось получить дату создания файла {}: {}", filename, e.getMessage());
            }

            return FileInfo.builder()
                    .filename(filename)
                    .size(fileSize)
                    .lastModified(lastModifiedDateTime)
                    .created(createdDateTime != null ? createdDateTime : lastModifiedDateTime)
                    .build();
        });
    }

    @Data
    @Builder
    public static class FileInfo {
        private String filename;
        private long size;
        private LocalDateTime lastModified;
        private LocalDateTime created;
    }
}

