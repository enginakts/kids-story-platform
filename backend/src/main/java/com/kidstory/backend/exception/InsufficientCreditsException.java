package com.kidstory.backend.exception;

public class InsufficientCreditsException extends ConflictException {

    public InsufficientCreditsException() {
        super("Masal krediniz kalmadı.");
    }
}
