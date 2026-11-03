package com.kidstory.backend;

import org.springframework.boot.SpringApplication;

/** Docker olmadan yerel çalıştırma: ./mvnw spring-boot:test-run */
public class LocalDevApplication {

    public static void main(String[] args) {
        System.setProperty("app.devdb.dir", ".devdb");
        SpringApplication.from(BackendApplication::main).with(EmbeddedPostgresConfig.class).run(args);
    }
}
