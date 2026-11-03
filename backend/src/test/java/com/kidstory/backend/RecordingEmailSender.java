package com.kidstory.backend;

import com.kidstory.backend.service.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Testlerde gönderilen e-postaları saklar; bağlantıdaki token'ı okumayı sağlar. */
public class RecordingEmailSender implements EmailSender {

    public record Mail(String to, String subject, String body) {
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Mail(to, subject, body));
    }

    public List<Mail> to(String address) {
        return sent.stream().filter(m -> m.to().equals(address)).toList();
    }

    public String lastToken(String address, String path) {
        List<Mail> mails = to(address);
        Matcher m = Pattern.compile(Pattern.quote(path) + "\\?token=([A-Za-z0-9_-]+)").matcher(mails.getLast().body());
        if (!m.find()) {
            throw new AssertionError("E-postada " + path + " bağlantısı yok");
        }
        return m.group(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }
}
