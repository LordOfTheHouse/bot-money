package dev.univer.shmel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramWrapper extends TelegramLongPollingBot {

    private final TelegramProperties props;
    private final ApplicationEventPublisher publisher;

    @Override public String getBotUsername() { return props.getUsername(); }
    @Override public String getBotToken() { return props.getToken(); }

    @Override
    public void onUpdateReceived(org.telegram.telegrambots.meta.api.objects.Update update) {
        log.debug("Incoming update: {}", update.getUpdateId());
        publisher.publishEvent(update);
    }

    public void installCommands() {
        List<BotCommand> commands = Arrays.asList(
                new BotCommand("/help", "Справка"),
                new BotCommand("/summary", "Показать сводку"),
                new BotCommand("/setinitial", "Начальная сумма"),
                new BotCommand("/adjust", "Прибавить/вычесть"),
                new BotCommand("/setbalance", "Установить баланс"),
                new BotCommand("/spent", "Расходы за дату"),
                new BotCommand("/spentall", "Расходы за всё время"),
                new BotCommand("/debt", "Записать долг"),
                new BotCommand("/pay", "Погасить долг"),
                new BotCommand("/debts", "Показать долги")
                                                 );
        SetMyCommands set = new SetMyCommands();
        set.setCommands(commands);
        set.setScope(new BotCommandScopeDefault());
        try { execute(set); log.info("Bot commands installed: {}", commands.size()); }
        catch (TelegramApiException e) { log.warn("Failed to set bot commands", e); }
    }
}