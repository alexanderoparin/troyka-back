package ru.oparin.troyka.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final String PASSWORD_PATTERN = 
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    @Override
    public void initialize(StrongPassword constraintAnnotation) {
        // Инициализация не требуется
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        // Проверяем длину
        if (password.length() < 8) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Пароль должен содержать минимум 8 символов")
                   .addConstraintViolation();
            return false;
        }

        // Проверяем наличие строчных букв (латинские и кириллические)
        if (!password.matches(".*[a-zа-я].*")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Пароль должен содержать строчные буквы")
                   .addConstraintViolation();
            return false;
        }

        // Проверяем наличие заглавных букв (латинские и кириллические)
        if (!password.matches(".*[A-ZА-Я].*")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Пароль должен содержать заглавные буквы")
                   .addConstraintViolation();
            return false;
        }

        // Проверяем наличие цифр
        if (!password.matches(".*\\d.*")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Пароль должен содержать цифры")
                   .addConstraintViolation();
            return false;
        }

        // Проверяем наличие специальных символов
        if (!password.matches(".*[@$!%*?&].*")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Пароль должен содержать специальные символы (@$!%*?&)")
                   .addConstraintViolation();
            return false;
        }

        return true;
    }
}
