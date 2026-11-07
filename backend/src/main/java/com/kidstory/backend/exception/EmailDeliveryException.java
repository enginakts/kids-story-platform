package com.kidstory.backend.exception;

/** E-posta SMTP sunucusuna iletilemedi (bağlantı, kimlik doğrulama veya sunucu hatası). */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(Throwable cause) {
        super("E-posta şu anda gönderilemedi. Lütfen biraz sonra tekrar deneyin.", cause);
    }
}
