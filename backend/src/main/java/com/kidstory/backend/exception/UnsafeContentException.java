package com.kidstory.backend.exception;

/** LLM çıktısı şemaya veya güvenlik kurallarına uymadı; iş yeniden denenir. Kullanıcıya gösterilmez. */
public class UnsafeContentException extends RuntimeException {

    public UnsafeContentException(String message) {
        super(message);
    }
}
