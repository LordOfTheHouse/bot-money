package dev.univer.shmel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TelegramExpenseBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(TelegramExpenseBotApplication.class, args);
    }
}
