package dev.univer.shmel.config;

import dev.univer.shmel.service.TelegramWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.InitializingBean;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@RequiredArgsConstructor
public class TelegramBotConfig {

    private final TelegramWrapper telegramWrapper;

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @Bean
    public InitializingBean registerBot(TelegramBotsApi api) {
        return () -> {
            try {
                api.registerBot(telegramWrapper);
                // Установить «быстрое» меню команд после регистрации
                telegramWrapper.installCommands();
            } catch (TelegramApiException e) {
                throw new RuntimeException("Failed to register Telegram bot", e);
            }
        };
    }
}