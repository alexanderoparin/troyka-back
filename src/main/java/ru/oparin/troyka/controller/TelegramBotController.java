package ru.oparin.troyka.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.telegram.TelegramUpdate;
import ru.oparin.troyka.service.telegram.TelegramBotService;

/**
 * Контроллер для обработки webhook от Telegram Bot API.
 */
@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramBotController {

    private final TelegramBotService telegramBotService;

    /**
     * Обработка webhook от Telegram.
     *
     * @param update объект обновления от Telegram
     * @return статус обработки
     */
    @PostMapping("/webhook")
    public Mono<ResponseEntity<String>> handleWebhook(@RequestBody TelegramUpdate update) {
        log.info("Получен webhook от Telegram: {}", update);

        return telegramBotService.processUpdate(update)
                .then(Mono.just(ResponseEntity.ok("OK")))
                .onErrorResume(error -> {
                    log.error("Ошибка обработки webhook: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.ok("ERROR"));
                });
    }

}
