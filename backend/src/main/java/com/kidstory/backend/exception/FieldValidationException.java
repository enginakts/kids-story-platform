package com.kidstory.backend.exception;

import lombok.Getter;

/** Bir form alanına bağlı iş kuralı hatası; web'de alanın altında, API'de errors içinde gösterilir. */
@Getter
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
