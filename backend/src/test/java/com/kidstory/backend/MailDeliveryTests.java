package com.kidstory.backend;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kayıt sonrası doğrulama e-postası gerçek SMTP protokolüyle (GreenMail sahte sunucusu) gönderilir. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.starttls.required=false",
        "app.mail.from=Masal Dünyası <no-reply@example.com>",
        "app.base-url=http://localhost:8080"})
class MailDeliveryTests {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registrationSendsRealVerificationEmailWhoseLinkWorks() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email))).andExpect(status().isCreated());

        assertThat(smtp.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage message = smtp.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(message.getFrom()[0].toString()).contains("no-reply@example.com");
        assertThat(message.getSubject()).isEqualTo("Masal Dünyası: e-posta adresinizi doğrulayın");
        String body = message.getContent().toString();
        assertThat(body).contains("Merhaba Ayşe");

        Matcher m = Pattern.compile("http://localhost:8080/verify-email\\?token=([A-Za-z0-9_-]+)").matcher(body);
        assertThat(m.find()).isTrue();
        mvc.perform(get("/verify-email").param("token", m.group(1))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT is_email_verified FROM parents WHERE email = ?", Boolean.class, email)).isTrue();
    }
}
