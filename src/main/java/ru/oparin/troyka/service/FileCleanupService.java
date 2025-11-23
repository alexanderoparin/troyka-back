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
                    return getInactiveUsersFiles()
                            .collectList()
                            .flatMap(inactiveUsersFiles -> {
                                Set<String> filesToKeep = calculateFilesToKeep(usedFileNames, inactiveUsersFiles);
                                return deleteUnusedFilesSafely(filesToKeep, daysOld);
                            });
                });
    }

    /**
     * Вычислить список файлов, которые нужно сохранить.
     * Исключает файлы неактивных пользователей из списка используемых файлов.
     *
     * @param usedFileNames множество используемых файлов
     * @param inactiveUsersFiles множество файлов неактивных пользователей
     * @return множество файлов для сохранения
     */
    private Set<String> calculateFilesToKeep(Set<String> usedFileNames, List<String> inactiveUsersFiles) {
        Set<String> filesToKeep = new HashSet<>(usedFileNames);
        filesToKeep.removeAll(inactiveUsersFiles);
        return filesToKeep;
    }

    /**
     * Безопасно удалить неиспользуемые файлы с обработкой ошибок.
     *
     * @param filesToKeep множество файлов для сохранения
     * @param daysOld минимальный возраст файла в днях
     * @return Mono с количеством удаленных файлов
     */
    private Mono<Integer> deleteUnusedFilesSafely(Set<String> filesToKeep, int daysOld) {
        return Mono.fromCallable(() -> deleteUnusedFiles(filesToKeep, daysOld))
                .onErrorResume(e -> {
                    log.error("Ошибка при очистке файлов", e);
                    return Mono.just(0);
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
        
        return imageGenerationHistoryRepository.findAll()
                .map(ImageGenerationHistory::getUserId)
                .distinct()
                .flatMap(userId -> processUserForInactiveFiles(userId, oneMonthAgo))
                .distinct();
    }

    /**
     * Обработать пользователя и вернуть его файлы, если он неактивен.
     *
     * @param userId ID пользователя
     * @param oneMonthAgo дата, до которой считается неактивным
     * @return поток файлов для удаления или пустой поток
     */
    private Flux<String> processUserForInactiveFiles(Long userId, LocalDateTime oneMonthAgo) {
        return paymentRepository.findByUserId(userId)
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                .hasElements()
                .flatMapMany(hasPayments -> {
                    if (hasPayments) {
                        return Flux.empty();
                    }
                    return getFilesForInactiveUser(userId, oneMonthAgo);
                });
    }

    /**
     * Получить файлы неактивного пользователя без платежей.
     *
     * @param userId ID пользователя
     * @param oneMonthAgo дата, до которой считается неактивным
     * @return поток файлов для удаления или пустой поток
     */
    private Flux<String> getFilesForInactiveUser(Long userId, LocalDateTime oneMonthAgo) {
        return imageGenerationHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .collectList()
                .flatMapMany(histories -> {
                    if (histories.isEmpty()) {
                        return Flux.empty();
                    }
                    
                    ImageGenerationHistory lastHistory = histories.get(0);
                    if (lastHistory.getCreatedAt().isBefore(oneMonthAgo)) {
                        log.info("Найден неактивный пользователь без платежей (ID: {}), последняя генерация: {}", 
                                userId, lastHistory.getCreatedAt());
                        
                        return extractFilesFromHistories(histories)
                                .doOnNext(file -> 
                                    log.debug("Файл неактивного пользователя без платежей для удаления: {}", file)
                                );
                    }
                    return Flux.empty();
                });
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
        processDirectoryRecursive(rootPath, directory, path -> {
            if (Files.isRegularFile(path)) {
                processUnusedFile(rootPath, path, usedFileNames, cutoffTime, deletedCount);
            }
        });
    }

    /**
     * Обработать файл и удалить его, если он не используется и старше указанного времени.
     *
     * @param rootPath корневой путь
     * @param filePath путь к файлу
     * @param usedFileNames множество используемых файлов
     * @param cutoffTime временная метка для проверки возраста
     * @param deletedCount счетчик удаленных файлов
     */
    private void processUnusedFile(Path rootPath, Path filePath, Set<String> usedFileNames,
                                   long cutoffTime, AtomicInteger deletedCount) {
        String relativePath = getRelativePath(rootPath, filePath);
        if (usedFileNames.contains(relativePath)) {
            return;
        }

        try {
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            if (lastModified < cutoffTime) {
                deleteOldUnusedFile(rootPath, filePath, lastModified, deletedCount);
            }
        } catch (IOException e) {
            log.warn("Не удалось обработать файл: {}", filePath, e);
        }
    }

    /**
     * Удалить старый неиспользуемый файл с логированием.
     *
     * @param rootPath корневой путь
     * @param filePath путь к файлу
     * @param lastModified время последнего изменения файла
     * @param deletedCount счетчик удаленных файлов
     */
    private void deleteOldUnusedFile(Path rootPath, Path filePath, long lastModified, AtomicInteger deletedCount) {
        try {
            String relativePath = getRelativePath(rootPath, filePath);
            long fileSize = Files.size(filePath);
            double fileSizeMB = fileSize / (1024.0 * 1024.0);
            long ageInDays = Duration.ofMillis(Instant.now().toEpochMilli() - lastModified).toDays();
            
            Files.delete(filePath);
            deletedCount.incrementAndGet();
            log.info("Удален старый неиспользуемый файл: {} (размер: {} МБ, возраст: {} дней)",
                    relativePath, String.format("%.2f", fileSizeMB), ageInDays);
        } catch (IOException e) {
            log.warn("Не удалось удалить файл: {}", filePath, e);
        }
    }

    /**
     * Экстренная очистка зараженных файлов (ransomware).
     * Удаляет файлы с подозрительными расширениями:
     * - .want_to_cry (зашифрованные файлы)
     * - .exe (подозрительные исполняемые файлы в директории загрузки)
     *
     * @return количество удаленных зараженных файлов
     */
    public Mono<Integer> cleanupInfectedFiles() {
        return Mono.fromCallable(() -> {
            AtomicInteger deletedCount = new AtomicInteger(0);
            Path uploadPath = Paths.get(uploadDir);
            
            if (!Files.exists(uploadPath)) {
                log.warn("Директория загрузки не существует: {}", uploadPath);
                return 0;
            }
            
            log.warn("Начата экстренная очистка зараженных файлов в директории: {}", uploadPath);
            processInfectedFiles(uploadPath, uploadPath, deletedCount);
            int count = deletedCount.get();
            log.warn("Экстренная очистка завершена. Удалено зараженных файлов: {}", count);
            return count;
        }).onErrorResume(e -> {
            log.error("Ошибка при экстренной очистке зараженных файлов", e);
            return Mono.just(0);
        });
    }

    /**
     * Обработать директорию и удалить зараженные файлы.
     * Удаляет:
     * 1. Все файлы с подозрительными расширениями (.want_to_cry, .exe, .lnk, .dat)
     * 2. Все поддиректории, кроме разрешенных (avatar, examples)
     * Рекурсивно обрабатывает только разрешенные поддиректории.
     *
     * @param rootPath корневой путь для вычисления относительных путей файлов
     * @param directory директория для обработки
     * @param deletedCount счетчик удаленных файлов (обновляется в процессе обработки)
     */
    private void processInfectedFiles(Path rootPath, Path directory, AtomicInteger deletedCount) {
        Set<String> allowedSubdirectories = Set.of("avatar", "examples");
        
        processDirectoryRecursive(rootPath, directory, path -> {
            if (Files.isDirectory(path)) {
                processInfectedDirectory(rootPath, path, allowedSubdirectories, deletedCount);
            } else if (Files.isRegularFile(path)) {
                processInfectedFile(rootPath, path, deletedCount);
            }
        });
    }

    /**
     * Обработать директорию на наличие зараженных файлов.
     *
     * @param rootPath корневой путь
     * @param directory директория для обработки
     * @param allowedSubdirectories множество разрешенных поддиректорий
     * @param deletedCount счетчик удаленных файлов
     */
    private void processInfectedDirectory(Path rootPath, Path directory, Set<String> allowedSubdirectories, 
                                         AtomicInteger deletedCount) {
        String dirName = directory.getFileName().toString();
        
        if (allowedSubdirectories.contains(dirName)) {
            processInfectedFiles(rootPath, directory, deletedCount);
        } else {
            String dirPath = getRelativePath(rootPath, directory);
            deleteDirectoryRecursively(directory, deletedCount);
            log.warn("Удалена неразрешенная поддиректория: {}", dirPath);
        }
    }

    /**
     * Обработать файл на наличие заражения.
     *
     * @param rootPath корневой путь
     * @param filePath путь к файлу
     * @param deletedCount счетчик удаленных файлов
     */
    private void processInfectedFile(Path rootPath, Path filePath, AtomicInteger deletedCount) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        
        if (isInfectedFile(fileName)) {
            deleteFileWithLogging(rootPath, filePath, deletedCount, "зараженный файл");
        }
    }

    /**
     * Проверить, является ли файл зараженным по его имени.
     *
     * @param fileName имя файла (в нижнем регистре)
     * @return true, если файл имеет подозрительное расширение
     */
    private boolean isInfectedFile(String fileName) {
        return fileName.endsWith(".want_to_cry") || 
               fileName.endsWith(".exe") || 
               fileName.endsWith(".lnk") || 
               fileName.endsWith(".dat") ||
               fileName.contains("want_to_cry");
    }

    /**
     * Получить относительный путь файла от корневого пути.
     *
     * @param rootPath корневой путь
     * @param filePath путь к файлу
     * @return относительный путь в формате с "/"
     */
    private String getRelativePath(Path rootPath, Path filePath) {
        return rootPath.relativize(filePath).toString().replace("\\", "/");
    }

    /**
     * Удалить файл с логированием размера.
     *
     * @param rootPath корневой путь для вычисления относительного пути
     * @param filePath путь к файлу для удаления
     * @param deletedCount счетчик удаленных файлов
     * @param fileType тип файла для логирования (например, "зараженный файл")
     */
    private void deleteFileWithLogging(Path rootPath, Path filePath, AtomicInteger deletedCount, String fileType) {
        try {
            String relativePath = getRelativePath(rootPath, filePath);
            long fileSize = Files.size(filePath);
            double fileSizeMB = fileSize / (1024.0 * 1024.0);
            Files.delete(filePath);
            deletedCount.incrementAndGet();
            log.warn("Удален {}: {} (размер: {} МБ)", fileType, relativePath, String.format("%.2f", fileSizeMB));
        } catch (IOException e) {
            log.error("Не удалось удалить {}: {}", fileType, filePath, e);
        }
    }

    /**
     * Рекурсивно удалить директорию со всем содержимым.
     *
     * @param directory директория для удаления
     * @param deletedCount счетчик удаленных файлов
     */
    private void deleteDirectoryRecursively(Path directory, AtomicInteger deletedCount) {
        try {
            Files.walk(directory)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                            deletedCount.incrementAndGet();
                        } catch (IOException e) {
                            log.error("Не удалось удалить файл/директорию: {}", file, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Не удалось удалить директорию: {}", directory, e);
        }
    }

    /**
     * Рекурсивно обработать директорию с применением обработчика к каждому пути.
     *
     * @param rootPath корневой путь для вычисления относительных путей
     * @param directory директория для обработки
     * @param pathHandler обработчик для каждого пути
     */
    private void processDirectoryRecursive(Path rootPath, Path directory, java.util.function.Consumer<Path> pathHandler) {
        try (Stream<Path> paths = Files.list(directory)) {
            paths.forEach(pathHandler);
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
            LocalDateTime lastModifiedDateTime = getLastModifiedTime(file);
            LocalDateTime createdDateTime = getCreationTime(file).orElse(lastModifiedDateTime);

            return FileInfo.builder()
                    .filename(filename)
                    .size(fileSize)
                    .lastModified(lastModifiedDateTime)
                    .created(createdDateTime)
                    .build();
        });
    }

    /**
     * Получить время последнего изменения файла.
     *
     * @param file путь к файлу
     * @return время последнего изменения
     */
    private LocalDateTime getLastModifiedTime(Path file) {
        try {
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault());
        } catch (IOException e) {
            log.warn("Не удалось получить время изменения файла: {}", file, e);
            return LocalDateTime.now();
        }
    }

    /**
     * Получить время создания файла.
     *
     * @param file путь к файлу
     * @return Optional с временем создания или пустой Optional, если не удалось получить
     */
    private Optional<LocalDateTime> getCreationTime(Path file) {
        try {
            Object creationTime = Files.getAttribute(file, "creationTime");
            if (creationTime != null) {
                long createdMillis = ((FileTime) creationTime).toMillis();
                return Optional.of(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(createdMillis),
                        ZoneId.systemDefault()
                ));
            }
        } catch (Exception e) {
            log.debug("Не удалось получить дату создания файла {}: {}", file, e.getMessage());
        }
        return Optional.empty();
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

