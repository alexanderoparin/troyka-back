package ru.oparin.troyka.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entity для таблицы art_styles.
 * Представляет стиль изображения с описанием промпта.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("art_styles")
public class ArtStyle {

    /** Название стиля (например, "Реалистичный", "Аниме") - используется как первичный ключ */
    @Id
    private String name;

    /** Промпт для добавления к основному промпту пользователя */
    private String prompt;
}

