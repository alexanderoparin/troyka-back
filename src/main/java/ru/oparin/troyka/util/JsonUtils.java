package ru.oparin.troyka.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Утилитный класс для работы с JSON.
 * Предоставляет методы для сериализации и десериализации простых структур данных.
 * 
 * TODO: В будущем заменить на Jackson или Gson для более надежной работы с JSON.
 */
@UtilityClass
@Slf4j
public class JsonUtils {

    /**
     * Преобразовать список строк в JSON строку.
     * Простая реализация без внешних библиотек.
     *
     * @param list список строк для сериализации
     * @return JSON строка в формате ["item1", "item2", ...] или null если список пустой
     */
    public static String convertListToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            // Экранируем кавычки и другие специальные символы
            String escapedItem = list.get(i)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            json.append("\"").append(escapedItem).append("\"");
        }
        json.append("]");
        
        return json.toString();
    }

    /**
     * Преобразовать JSON объект в список строк.
     * Обрабатывает как строки, так и другие типы объектов.
     *
     * @param jsonObject JSON объект (может быть строкой или другим типом)
     * @return список строк или пустой список если JSON некорректный
     */
    public static List<String> parseJsonToList(Object jsonObject) {
        if (jsonObject == null) {
            return List.of();
        }
        
        String json = jsonObject.toString();
        
        // Обрабатываем JsonByteArrayInput формат
        if (json.startsWith("JsonByteArrayInput{") && json.endsWith("}")) {
            // Извлекаем содержимое из JsonByteArrayInput{...}
            json = json.substring("JsonByteArrayInput{".length(), json.length() - 1);
        }
        
        return parseJsonToList(json);
    }

    /**
     * Преобразовать JSON строку в список строк.
     * Простая реализация для парсинга JSON массива строк.
     *
     * @param json JSON строка в формате ["item1", "item2", ...]
     * @return список строк или пустой список если JSON некорректный
     */
    public static List<String> parseJsonToList(String json) {
        if (json == null || json.trim().isEmpty() || json.equals("null")) {
            return List.of();
        }
        
        try {
            String content = json.trim();
            
            // Проверяем, что это JSON массив
            if (!content.startsWith("[") || !content.endsWith("]")) {
                return List.of();
            }
            
            // Убираем квадратные скобки
            content = content.substring(1, content.length() - 1).trim();
            
            if (content.isEmpty()) {
                return List.of();
            }
            
            // Простой парсинг элементов массива
            return List.of(content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) // Разделяем по запятым, не внутри кавычек
                    .stream()
                    .map(String::trim)
                    .map(item -> {
                        // Убираем кавычки и экранируем обратно
                        if (item.startsWith("\"") && item.endsWith("\"")) {
                            return item.substring(1, item.length() - 1)
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                                    .replace("\\n", "\n")
                                    .replace("\\r", "\r")
                                    .replace("\\t", "\t");
                        }
                        return item;
                    })
                    .filter(item -> !item.isEmpty())
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Убираем blob из URL'ов.
     */
    public static List<String> removingBlob(List<String> inputImageUrls) {
        return inputImageUrls.stream()
                .map(url -> url.startsWith("blob:") ? url.substring(5) : url)
                .toList();
    }
}
