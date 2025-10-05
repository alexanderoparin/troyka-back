package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.contact.ContactRequest;
import ru.oparin.troyka.model.dto.contact.ContactResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:noreply@24reshai.ru}")
    private String fromEmail;

    @Value("${app.email.support:support@24reshai.ru}")
    private String supportEmail;

    public Mono<ContactResponse> sendContactMessage(ContactRequest request) {
        log.info("Получено сообщение от контактной формы: {} <{}>", request.getName(), request.getEmail());
        
        return Mono.fromCallable(() -> {
            try {
                // Генерируем уникальный ID сообщения
                String messageId = generateMessageId();
                
                // Отправляем письмо в поддержку
                sendEmailToSupport(request, messageId);
                
                // Отправляем подтверждение отправителю
                sendConfirmationEmail(request, messageId);
                
                log.info("Сообщение {} успешно отправлено от {} <{}>", messageId, request.getName(), request.getEmail());
                
                return ContactResponse.builder()
                        .message("Ваше сообщение успешно отправлено! Мы ответим в течение 24 часов.")
                        .status("success")
                        .messageId(messageId)
                        .build();
                        
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения от {} <{}>", request.getName(), request.getEmail(), e);
                throw new RuntimeException("Ошибка при отправке сообщения: " + e.getMessage());
            }
        });
    }

    private void sendEmailToSupport(ContactRequest request, String messageId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(supportEmail);
        message.setSubject(String.format("[24reshai.ru] %s - %s", messageId, request.getSubject()));
        message.setText(buildSupportEmailContent(request, messageId));
        
        mailSender.send(message);
        log.info("Письмо в поддержку отправлено: {}", messageId);
    }

    private void sendConfirmationEmail(ContactRequest request, String messageId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(request.getEmail());
        message.setSubject("Подтверждение получения сообщения - 24reshai.ru");
        message.setText(buildConfirmationEmailContent(request, messageId));
        
        mailSender.send(message);
        log.info("Письмо-подтверждение отправлено: {} -> {}", messageId, request.getEmail());
    }

    private String buildSupportEmailContent(ContactRequest request, String messageId) {
        return String.format("""
                ============================================
                НОВОЕ СООБЩЕНИЕ ЧЕРЕЗ КОНТАКТНУЮ ФОРМУ
                ID: %s
                Время: %s
                ============================================
                
                От: %s <%s>
                %s
                
                Тема: %s
                
                Сообщение:
                %s
                
                ============================================
                Ответить отправителю: %s
                ============================================
                """,
                messageId,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")),
                request.getName(),
                request.getEmail(),
                request.getPhone() != null ? "Телефон: " + request.getPhone() : "Телефон не указан",
                request.getSubject(),
                request.getMessage(),
                request.getEmail()
        );
    }

    private String buildConfirmationEmailContent(ContactRequest request, String messageId) {
        return String.format("""
                Здравствуйте, %s!
                
                Спасибо за обращение в службу поддержки 24reshai.ru!
                
                Ваше сообщение получено и будет рассмотрено нашими специалистами.
                
                Детали сообщения:
                • ID сообщения: %s
                • Тема: %s
                • Время отправки: %s
                
                Мы ответим на ваше сообщение в течение 24 часов.
                
                Если у вас срочный вопрос, вы можете связаться с нами напрямую:
                • Email: support@24reshai.ru
                • Сайт: https://24reshai.ru
                
                С уважением,
                Команда поддержки 24reshai.ru
                """,
                request.getName(),
                messageId,
                request.getSubject(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
        );
    }

    private String generateMessageId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("contact_%s_%s", timestamp, uuid);
    }
}
