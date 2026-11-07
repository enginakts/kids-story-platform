package com.kidstory.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Masal oluşturmak için önce e-posta adresinizi doğrulayın.");
    }
}
