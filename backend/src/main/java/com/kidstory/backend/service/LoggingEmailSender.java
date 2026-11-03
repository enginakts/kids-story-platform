package com.kidstory.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Geliştirmede e-posta göndermek yerine içeriği log'a yazar. Üretimde kullanılmaz. */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("E-POSTA (gönderilmedi, yalnızca geliştirme)\nAlıcı: {}\nKonu: {}\n{}", to, subject, body);
    }
}
