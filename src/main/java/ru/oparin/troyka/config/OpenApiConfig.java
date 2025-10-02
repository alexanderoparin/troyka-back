package ru.oparin.troyka.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "24reshai API",
                version = "1.0.0",
                description = "Документация API для 24reshai Backend приложения",
                contact = @Contact(
                        name = "Support",
                        email = "support@troyka.ru"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Локальный сервер"),
                @Server(url = "http://213.171.4.47:8080", description = "Продакшен сервер")
        }
)
@Configuration
public class OpenApiConfig {
}