package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для пагинированного ответа API.
 * Содержит данные страницы и метаинформацию о пагинации.
 *
 * @param <T> тип элементов в списке
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDTO<T> {

    /** Список элементов на текущей странице */
    private List<T> content;

    /** Номер текущей страницы (начиная с 0) */
    private Integer page;

    /** Размер страницы */
    private Integer size;

    /** Общее количество элементов */
    private Long totalElements;

    /** Общее количество страниц */
    private Integer totalPages;

    /** Есть ли следующая страница */
    private Boolean hasNext;

    /** Есть ли предыдущая страница */
    private Boolean hasPrevious;

    /** Это первая страница */
    private Boolean isFirst;

    /** Это последняя страница */
    private Boolean isLast;

    /**
     * Создает пагинированный ответ с автоматическим расчетом метаинформации.
     * 
     * @param content список элементов на текущей странице
     * @param page номер текущей страницы (начиная с 0)
     * @param size размер страницы
     * @param totalElements общее количество элементов
     * @return пагинированный ответ
     */
    public static <T> PageResponseDTO<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        
        return PageResponseDTO.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .isFirst(page == 0)
                .isLast(page >= totalPages - 1)
                .build();
    }
}
