package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.contact.ContactRequest;
import ru.oparin.troyka.model.dto.contact.ContactResponse;
import ru.oparin.troyka.service.ContactService;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/contact")
@Tag(name = "Контактная форма", description = "API для отправки сообщений через контактную форму")
public class ContactController {

    private final ContactService contactService;

    @Operation(summary = "Отправить сообщение через контактную форму",
            description = "Отправляет сообщение в службу поддержки и подтверждение отправителю")
    @PostMapping("/send")
    public Mono<ResponseEntity<ContactResponse>> sendMessage(@Valid @RequestBody ContactRequest request) {
        log.info("Получен запрос на отправку сообщения от: {} <{}>", request.getName(), request.getEmail());
        
        return contactService.sendContactMessage(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка при отправке сообщения от {} <{}>", request.getName(), request.getEmail(), e);
                    return Mono.just(ResponseEntity.badRequest().body(
                            ContactResponse.builder()
                                    .message("Произошла ошибка при отправке сообщения. Попробуйте позже.")
                                    .status("error")
                                    .build()
                    ));
                });
    }
}
