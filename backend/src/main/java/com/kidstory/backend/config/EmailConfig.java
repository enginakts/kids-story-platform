package com.kidstory.backend.config;

import com.kidstory.backend.service.EmailSender;
import com.kidstory.backend.service.LoggingEmailSender;
import com.kidstory.backend.service.SmtpEmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * spring.mail.host tanımlıysa SMTP ile gönderilir. Tanımlı değilse geliştirmede log'a yazılır;
 * üretimde hiçbir gönderici oluşmaz ve uygulama başlamaz (bağlantılar log'a sızmaz).
 */
@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.mail.host")
    EmailSender smtpEmailSender(JavaMailSender mail, @Value("${app.mail.from}") String from) {
        return new SmtpEmailSender(mail, from);
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}
