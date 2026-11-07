package com.kidstory.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SMTP sunucusuna ulaşılamazsa kayıt yine tamamlanır, yeniden gönderimde hata kullanıcıya gösterilir. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1",
        "spring.mail.properties.mail.smtp.connectiontimeout=2000",
        "app.mail.from=no-reply@example.com"})
class MailFailureTests {

    @Autowired MockMvc mvc;

    @Test
    void unreachableSmtpDoesNotBreakRegistrationButIsReported() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String token = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Parola123!","firstName":"A","lastName":"B","consent":true}
                        """.formatted(email))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(post("/api/v1/account/verify-email/resend").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));
        mvc.perform(post("/app/verify-email/resend").session(session).with(csrf()))
                .andExpect(redirectedUrl("/app?verificationFailed"));

        // Parola sıfırlamada hata dışarı yansımaz: kayıtlı olup olmadığı anlaşılmasın.
        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email))).andExpect(status().isAccepted());
    }
}
