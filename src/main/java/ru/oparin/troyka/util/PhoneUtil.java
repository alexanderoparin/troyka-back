package ru.oparin.troyka.util;

import lombok.experimental.UtilityClass;

/**
 * Утилитный класс для работы с номерами телефонов
 */
@UtilityClass
public class PhoneUtil {

    /**
     * Нормализует номер телефона к единому формату
     * Убирает все символы кроме цифр и знака +
     * 
     * @param phone исходный номер телефона
     * @return нормализованный номер или null если входной параметр null/пустой
     */
    public static String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        
        // Убираем все пробелы, скобки, дефисы и другие символы кроме цифр и +
        String normalized = phone.replaceAll("[^0-9+]", "");
        
        // Если номер начинается с 8, заменяем на +7
        if (normalized.startsWith("8") && normalized.length() == 11) {
            normalized = "+7" + normalized.substring(1);
        }
        
        // Если номер начинается с 7 без +, добавляем +
        if (normalized.startsWith("7") && !normalized.startsWith("+7") && normalized.length() == 11) {
            normalized = "+" + normalized;
        }
        
        return normalized;
    }
    
    /**
     * Проверяет, является ли строка валидным номером телефона
     * 
     * @param phone номер телефона
     * @return true если номер валидный
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        
        String normalized = normalizePhone(phone);
        if (normalized == null) {
            return false;
        }
        
        // Проверяем российские номера: +7XXXXXXXXXX (11 цифр после +7)
        if (normalized.startsWith("+7") && normalized.length() == 12) {
            return true;
        }
        
        // Проверяем международные номера: +XXXXXXXXX (от 8 до 15 цифр после +)
        if (normalized.startsWith("+") && normalized.length() >= 9 && normalized.length() <= 16) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Форматирует номер телефона для отображения
     * 
     * @param phone нормализованный номер телефона
     * @return отформатированный номер
     */
    public static String formatPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        
        String normalized = normalizePhone(phone);
        if (normalized == null) {
            return phone; // Возвращаем исходный если не удалось нормализовать
        }
        
        // Форматируем российские номера: +7 (999) 123-45-67
        if (normalized.startsWith("+7") && normalized.length() == 12) {
            String digits = normalized.substring(2);
            return String.format("+7 (%s) %s-%s-%s", 
                digits.substring(0, 3),
                digits.substring(3, 6),
                digits.substring(6, 8),
                digits.substring(8, 10)
            );
        }
        
        // Для других номеров возвращаем как есть
        return normalized;
    }
}
