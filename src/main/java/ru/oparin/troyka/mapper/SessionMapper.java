package ru.oparin.troyka.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.troyka.model.dto.*;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.model.entity.Session;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Компонент для преобразования сущностей сессий в DTO.
 * Предоставляет методы для маппинга объектов Session и ImageGenerationHistory.
 */
@Component
@Slf4j
public class SessionMapper {

    /**
     * Преобразует сущность Session в SessionDTO.
     * 
     * @param session сущность сессии
     * @return DTO сессии
     */
    public SessionDTO toSessionDTO(Session session) {
        if (session == null) {
            return null;
        }
        
        return SessionDTO.builder()
                .id(session.getId())
                .name(session.getName())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .lastImageUrl(null) // Будет заполнено в сервисе при необходимости
                .messageCount(0) // Будет заполнено в сервисе при необходимости
                .build();
    }

    /**
     * Преобразует сущность Session в CreateSessionResponseDTO.
     * 
     * @param session сущность сессии
     * @return DTO ответа при создании сессии
     */
    public CreateSessionResponseDTO toCreateSessionResponseDTO(Session session) {
        if (session == null) {
            return null;
        }
        
        return CreateSessionResponseDTO.builder()
                .id(session.getId())
                .name(session.getName())
                .createdAt(session.getCreatedAt())
                .message("Сессия успешно создана")
                .build();
    }

    /**
     * Преобразует сущность Session в RenameSessionResponseDTO.
     * 
     * @param session сущность сессии
     * @return DTO ответа при переименовании сессии
     */
    public RenameSessionResponseDTO toRenameSessionResponseDTO(Session session) {
        if (session == null) {
            return null;
        }
        
        return RenameSessionResponseDTO.builder()
                .id(session.getId())
                .name(session.getName())
                .message("Сессия успешно переименована")
                .build();
    }

    /**
     * Преобразует сущность ImageGenerationHistory в SessionMessageDTO.
     * 
     * @param history сущность истории генерации
     * @return DTO сообщения сессии
     */
    public SessionMessageDTO toSessionMessageDTO(ImageGenerationHistory history) {
        if (history == null) {
            return null;
        }
        
        // Получаем список сгенерированных изображений
        List<String> imageUrls = history.getImageUrls();
        
        // Получаем список входных изображений
        List<String> inputImageUrls = history.getInputImageUrls();
        
        return SessionMessageDTO.builder()
                .id(history.getId())
                .prompt(history.getPrompt())
                .imageUrls(imageUrls)
                .inputImageUrls(inputImageUrls)
                .createdAt(history.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .imageCount(imageUrls.size())
                .outputFormat("JPEG") // TODO: Получить из истории или добавить поле
                .build();
    }

    /**
     * Преобразует список сущностей ImageGenerationHistory в список SessionMessageDTO.
     * 
     * @param histories список сущностей истории генерации
     * @return список DTO сообщений сессии
     */
    public List<SessionMessageDTO> toSessionMessageDTOList(List<ImageGenerationHistory> histories) {
        if (histories == null) {
            return null;
        }
        
        return histories.stream()
                .map(this::toSessionMessageDTO)
                .toList();
    }

    /**
     * Преобразует сущность Session и связанную историю в SessionDetailDTO.
     * 
     * @param session сущность сессии
     * @param histories список истории генерации
     * @param totalMessages общее количество сообщений
     * @param hasMore есть ли еще сообщения для загрузки
     * @return DTO детальной информации о сессии
     */
    public SessionDetailDTO toSessionDetailDTO(Session session, 
                                            List<ImageGenerationHistory> histories,
                                            int totalMessages,
                                            boolean hasMore) {
        if (session == null) {
            return null;
        }
        
        return SessionDetailDTO.builder()
                .id(session.getId())
                .name(session.getName())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .history(toSessionMessageDTOList(histories))
                .totalMessages(totalMessages)
                .hasMore(hasMore)
                .build();
    }

    /**
     * Преобразует результат удаления сессии в DeleteSessionResponseDTO.
     * 
     * @param sessionId идентификатор удаленной сессии
     * @param deletedHistoryCount количество удаленных записей истории
     * @return DTO ответа при удалении сессии
     */
    public DeleteSessionResponseDTO toDeleteSessionResponseDTO(Long sessionId, Integer deletedHistoryCount) {
        if (sessionId == null) {
            return null;
        }
        
        return DeleteSessionResponseDTO.builder()
                .id(sessionId)
                .message("Сессия успешно удалена")
                .deletedHistoryCount(deletedHistoryCount)
                .build();
    }

    /**
     * Преобразует CreateSessionResponseDTO в SessionDTO.
     * 
     * @param createResponse DTO ответа при создании сессии
     * @return DTO сессии
     */
    public SessionDTO createResponseToSessionDTO(CreateSessionResponseDTO createResponse) {
        if (createResponse == null) {
            return null;
        }
        
        return SessionDTO.builder()
                .id(createResponse.getId())
                .name(createResponse.getName())
                .createdAt(createResponse.getCreatedAt())
                .updatedAt(createResponse.getCreatedAt()) // Используем createdAt как updatedAt
                .lastImageUrl(null)
                .messageCount(0)
                .build();
    }

    /**
     * Преобразует Session в SessionDTO с дополнительной информацией.
     * 
     * @param session сущность сессии
     * @param lastImageUrl URL последнего изображения
     * @param messageCount количество сообщений
     * @return DTO сессии с дополнительной информацией
     */
    public SessionDTO toSessionDTOWithDetails(Session session, String lastImageUrl, int messageCount) {
        if (session == null) {
            return null;
        }
        
        return SessionDTO.builder()
                .id(session.getId())
                .name(session.getName())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .lastImageUrl(lastImageUrl)
                .messageCount(messageCount)
                .build();
    }

    /**
     * Преобразует SessionDTO в Session сущность.
     * 
     * @param sessionDTO DTO сессии
     * @return сущность Session
     */
    public Session toSessionEntity(SessionDTO sessionDTO) {
        if (sessionDTO == null) {
            return null;
        }
        
        return Session.builder()
                .id(sessionDTO.getId())
                .name(sessionDTO.getName())
                .createdAt(sessionDTO.getCreatedAt())
                .updatedAt(sessionDTO.getUpdatedAt())
                .isActive(true)
                .build();
    }

    /**
     * Создает новую сущность Session для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param sessionName название сессии (опционально)
     * @return новая сущность Session
     */
    public Session createSessionEntity(Long userId, String sessionName) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        String name = (sessionName != null && !sessionName.trim().isEmpty()) 
            ? sessionName.trim() 
            : "Сессия";
        
        return Session.builder()
                .userId(userId)
                .name(name)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isActive(true)
                .build();
    }
}
