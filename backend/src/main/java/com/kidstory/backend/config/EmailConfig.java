package com.kidstory.backend.config;

import com.kidstory.backend.service.EmailSender;
import com.kidstory.backend.service.LoggingEmailSender;
import com.kidstory.backend.service.SmtpEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * spring.mail.host doluysa SMTP ile gönderilir (geliştirmede de). Boşsa geliştirmede e-posta gönderilmez,
 * bağlantılar log'a yazılır; üretimde hiçbir gönderici oluşmaz ve uygulama başlamaz.
 * Geliştirme ayarları için git'e girmeyen backend/config/application-dev.properties kullanılır.
 */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    @ConditionalOnExpression("'${spring.mail.host:}' != ''")
    EmailSender smtpEmailSender(JavaMailSender mail, @Value("${spring.mail.host}") String host,
                                @Value("${app.mail.from:}") String from) {
        if (from.isBlank()) {
            throw new IllegalStateException("SMTP tanımlı ama gönderen adresi yok: app.mail.from (MAIL_FROM) veya spring.mail.username verin");
        }
        log.info("E-posta gönderimi: SMTP ({})", host);
        return new SmtpEmailSender(mail, from);
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender loggingEmailSender() {
        log.warn("E-posta GÖNDERİLMİYOR: SMTP tanımlı değil. Doğrulama ve sıfırlama bağlantıları bu log'a yazılacak. "
                + "Gerçek gönderim için backend/config/application-dev.properties dosyasına spring.mail.* ayarlarını ekleyin.");
        return new LoggingEmailSender();
    }
}
