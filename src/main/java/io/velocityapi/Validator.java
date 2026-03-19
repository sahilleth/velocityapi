package io.velocityapi;

import io.velocityapi.annotations.Email;
import io.velocityapi.annotations.Max;
import io.velocityapi.annotations.Min;
import io.velocityapi.annotations.NotBlank;
import io.velocityapi.annotations.NotNull;
import io.velocityapi.annotations.Size;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Validator {
    private Validator() {}

    public static void validate(Object obj) throws ValidationException {
        if (obj == null) return;

        List<ValidationError> errors = new ArrayList<>();

        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);

            Object value;
            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access field for validation: " + field.getName(), e);
            }

            String name = field.getName();

            if (field.isAnnotationPresent(NotNull.class) && value == null) {
                errors.add(new ValidationError(name, "must not be null"));
            }

            if (field.isAnnotationPresent(NotBlank.class)) {
                if (value == null || value.toString().isBlank()) {
                    errors.add(new ValidationError(name, "must not be blank"));
                }
            }

            if (field.isAnnotationPresent(Email.class) && value != null) {
                if (!value.toString().matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    errors.add(new ValidationError(name, "must be a valid email address"));
                }
            }

            if (field.isAnnotationPresent(Min.class) && value instanceof Number n) {
                long min = field.getAnnotation(Min.class).value();
                if (n.longValue() < min) {
                    errors.add(new ValidationError(name, "must be >= " + min));
                }
            }

            if (field.isAnnotationPresent(Max.class) && value instanceof Number n) {
                long max = field.getAnnotation(Max.class).value();
                if (n.longValue() > max) {
                    errors.add(new ValidationError(name, "must be <= " + max));
                }
            }

            if (field.isAnnotationPresent(Size.class) && value != null) {
                Size s = field.getAnnotation(Size.class);
                int len;

                if (value instanceof Collection<?> c) {
                    len = c.size();
                } else {
                    len = value.toString().length();
                }

                if (len < s.min() || len > s.max()) {
                    errors.add(new ValidationError(name, "size must be between " + s.min() + " and " + s.max()));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}

