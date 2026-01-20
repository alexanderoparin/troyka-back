package ru.oparin.troyka.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.service.FileService;
import ru.oparin.troyka.util.SecurityUtil;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public Mono<ResponseEntity<String>>uploadFile(@RequestPart("file") FilePart filePart) {
        return SecurityUtil.getCurrentUsername()
                .doOnNext(username -> log.info("Получен запрос на загрузку файла от пользователя: {}", username))
                .flatMap(username -> fileService.saveFile(filePart, username))
                .onErrorResume(e -> {
                    log.error("Ошибка при загрузке файла", e);
                    return Mono.just(ResponseEntity.badRequest().body("Ошибка при загрузке файла: " + e.getMessage()));
                });
    }

    @GetMapping("/avatar/{filename}")
    public Mono<ResponseEntity<Resource>> serveAvatarFile(@PathVariable String filename) {
        return fileService.getFile("avatar/" + filename);
    }

    @GetMapping("/examples/{filename}")
    public Mono<ResponseEntity<Resource>> getExampleFile(@PathVariable String filename) {
        return fileService.getExampleFile(filename);
    }

    @GetMapping("/{subdirectory}/{filename:.+}")
    public Mono<ResponseEntity<Resource>> serveFileInSubdirectory(
            @PathVariable String subdirectory,
            @PathVariable String filename) {
        return fileService.getFile(subdirectory + "/" + filename);
    }

    @GetMapping("/{filename:.+}")
    public Mono<ResponseEntity<Resource>> serveFile(@PathVariable String filename) {
        return fileService.getFile(filename);
    }
}