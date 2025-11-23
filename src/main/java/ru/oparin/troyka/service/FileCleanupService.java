package ru.oparin.troyka.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.model.enums.PaymentStatus;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.repository.PaymentRepository;

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
 * <p>
 * Предоставляет функциональность для удаления файлов, которые:
 * <ul>
 *   <li>Не используются в базе данных и старше указанного возраста</li>
 *   <li>Принадлежат пользователям, которые никогда не оплачивали и генерировали изображения больше месяца назад</li>
 * </ul>
 * <p>
 * Сервис использует реактивные потоки (Reactor) для эффективной обработки больших объемов данных.
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
    private final PaymentRepository paymentRepository;

    /**
     * Очистить старые неиспользуемые файлы.
     * Удаляет:
     * 1. Файлы, которые не используются в БД и старше указанного количества дней
     * 2. Файлы пользователей, которые никогда не оплачивали и генерировали изображения больше месяца назад
     *
     * @param daysOld минимальный возраст файла в днях для удаления
     * @return количество удаленных файлов
     */
    public Mono<Integer> cleanupOldUnusedFiles(int daysOld) {
        return getAllUsedFileNames()
                .collectList()
                .flatMap(usedFileNamesList -> {
                    Set<String> usedFileNames = new HashSet<>(usedFileNamesList);
                    // Получаем файлы неактивных пользователей без платежей
                    return getInactiveUsersFiles()
                            .collectList()
                            .flatMap(inactiveUsersFiles -> {
                                // Объединяем файлы неактивных пользователей с используемыми
                                // Файлы неактивных пользователей будут удалены, даже если они в БД
                                Set<String> filesToKeep = new HashSet<>(usedFileNames);
                                filesToKeep.removeAll(inactiveUsersFiles);
                                
                                return Mono.fromCallable(() -> deleteUnusedFiles(filesToKeep, daysOld))
                                        .onErrorResume(e -> {
                                            log.error("Ошибка при очистке файлов", e);
                                            return Mono.just(0);
                                        });
                            });
                });
    }

    /**
     * Получить файлы пользователей, которые никогда не оплачивали
     * и генерировали изображения больше месяца назад.
     *
     * @return поток имен файлов для удаления
     */
    private Flux<String> getInactiveUsersFiles() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        
        // Получаем всех пользователей, у которых есть генерации
        return imageGenerationHistoryRepository.findAll()
                .map(ImageGenerationHistory::getUserId)
                .distinct()
                .flatMap(userId -> {
                    // Проверяем, есть ли у пользователя успешные платежи
                    return paymentRepository.findByUserId(userId)
                            .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                            .hasElements()
                            .flatMapMany(hasPayments -> {
                                if (hasPayments) {
                                    // У пользователя есть платежи, пропускаем
                                    return Flux.empty();
                                }
                                
                                // У пользователя нет платежей, получаем все его генерации
                                return imageGenerationHistoryRepository
                                        .findByUserIdOrderByCreatedAtDesc(userId)
                                        .collectList()
                                        .flatMapMany(histories -> {
                                            if (histories.isEmpty()) {
                                                return Flux.empty();
                                            }
                                            
                                            // Проверяем последнюю генерацию
                                            ImageGenerationHistory lastHistory = histories.get(0);
                                            if (lastHistory.getCreatedAt().isBefore(oneMonthAgo)) {
                                                // Последняя генерация была больше месяца назад
                                                log.info("Найден неактивный пользователь без платежей (ID: {}), последняя генерация: {}", 
                                                        userId, lastHistory.getCreatedAt());
                                                
                                                return extractFilesFromHistories(histories)
                                                        .doOnNext(file -> 
                                                            log.debug("Файл неактивного пользователя без платежей для удаления: {}", file)
                                                        );
                                            }
                                            return Flux.empty();
                                        });
                            });
                })
                .distinct();
    }

    /**
     * Получить все используемые имена файлов из истории генераций.
     *
     * @return поток имен используемых файлов
     */
    private Flux<String> getAllUsedFileNames() {
        return imageGenerationHistoryRepository.findAll()
                .flatMap(this::extractFilesFromHistory)
                .distinct();
    }

    /**
     * Извлечь имена файлов из истории генерации.
     *
     * @param history история генерации
     * @return поток имен файлов
     */
    private Flux<String> extractFilesFromHistory(ImageGenerationHistory history) {
        Set<String> files = new HashSet<>();
        List<String> inputImageUrls = history.getInputImageUrls();
        if (inputImageUrls != null) {
            inputImageUrls.forEach(url -> extractFileName(url).ifPresent(files::add));
        }
        return Flux.fromIterable(files);
    }

    /**
     * Извлечь имена файлов из списка историй генераций.
     *
     * @param histories список историй генераций
     * @return поток имен файлов
     */
    private Flux<String> extractFilesFromHistories(List<ImageGenerationHistory> histories) {
        return Flux.fromIterable(histories)
                .flatMap(this::extractFilesFromHistory)
                .distinct();
    }

    /**
     * Извлечь имя файла из полного URL.
     * Извлекает относительный путь файла из URL вида "https://host/files/path/to/file.jpg".
     *
     * @param url полный URL файла
     * @return имя файла (относительный путь) или пустой Optional, если URL не соответствует ожидаемому формату
     */
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

    /**
     * Удалить неиспользуемые файлы из директории загрузки.
     * Удаляет файлы, которые не входят в список используемых файлов и старше указанного возраста.
     *
     * @param usedFileNames множество имен файлов, которые используются в системе (не должны быть удалены)
     * @param daysOld минимальный возраст файла в днях для удаления
     * @return количество удаленных файлов
     */
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

    /**
     * Обработать директорию и удалить неиспользуемые файлы.
     * Рекурсивно обходит директорию и удаляет файлы, которые не используются и старше указанного времени.
     *
     * @param rootPath корневой путь для вычисления относительных путей файлов
     * @param directory директория для обработки
     * @param usedFileNames множество имен используемых файлов (не должны быть удалены)
     * @param cutoffTime временная метка в миллисекундах - файлы старше этой метки могут быть удалены
     * @param deletedCount счетчик удаленных файлов (обновляется в процессе обработки)
     */
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
                            double fileSizeMB = fileSize / (1024.0 * 1024.0);
                            long ageInDays = Duration.ofMillis(Instant.now().toEpochMilli() - lastModified).toDays();
                            Files.delete(path);
                            deletedCount.incrementAndGet();
                            log.info("Удален старый неиспользуемый файл: {} (размер: {} МБ, возраст: {} дней)",
                                    filePath, String.format("%.2f", fileSizeMB), ageInDays);
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

    /**
     * Информация о файле на диске.
     * Содержит метаданные файла: имя, размер, даты создания и изменения.
     */
    @Data
    @Builder
    public static class FileInfo {
        /**
         * Имя файла (относительный путь от корня директории загрузки).
         */
        private String filename;
        
        /**
         * Размер файла в байтах.
         */
        private long size;
        
        /**
         * Дата и время последнего изменения файла.
         */
        private LocalDateTime lastModified;
        
        /**
         * Дата и время создания файла.
         * Если дата создания недоступна, используется дата последнего изменения.
         */
        private LocalDateTime created;
    }
}

