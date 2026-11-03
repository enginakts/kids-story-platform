package com.kidstory.backend;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;

/** Docker gerektirmeyen gerçek PostgreSQL 16. app.devdb.dir verilirse veri kalıcıdır. */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfig {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres(@Value("${app.devdb.dir:}") String dir) throws IOException {
        // Windows'un "Turkish_Türkiye.1254" yerel ayar adını initdb reddettiği için C kullanılır; metinler UTF-8 kalır.
        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder().setLocaleConfig("locale", "C");
        if (!dir.isBlank()) {
            builder.setDataDirectory(new File(dir)).setCleanDataDirectory(false);
        }
        return builder.start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
