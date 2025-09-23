package ru.oparin.troyka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@EnableR2dbcRepositories(basePackages = "ru.oparin.troyka.repository")
@SpringBootApplication
public class TroykaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TroykaApplication.class, args);
    }
}