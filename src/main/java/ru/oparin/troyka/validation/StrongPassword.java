package ru.oparin.troyka.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Пароль должен содержать минимум 8 символов, включая заглавные и строчные буквы, цифры и специальные символы";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
