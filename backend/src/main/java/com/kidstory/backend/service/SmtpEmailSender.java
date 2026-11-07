package com.kidstory.backend.service;

import com.kidstory.backend.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Herhangi bir SMTP sağlayıcısıyla (Gmail, Brevo, kurumsal sunucu) düz metin e-posta gönderir. */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mail;
    private final String from;

    public SmtpEmailSender(JavaMailSender mail, String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mail.send(message);
        } catch (MailException e) {
            // Alıcı adresi ve içerik log'a yazılmaz; yalnızca hata türü.
            log.warn("SMTP gönderimi başarısız: {}", e.getMostSpecificCause().toString());
            throw new EmailDeliveryException(e);
        }
    }
}
