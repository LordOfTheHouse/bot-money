package dev.univer.shmel.service;

import dev.univer.shmel.model.ChatSettings;
import dev.univer.shmel.repo.ChatSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
@RequiredArgsConstructor
public class ChatSettingsService {
    private final ChatSettingsRepository repo;

    @Value("${bot.summaryTime:23:59}")
    private String defaultSummaryTime;

    @Value("${bot.defaultZoneId:UTC}")
    private String defaultZoneId;

    @Transactional
    public ChatSettings ensure(Long chatId, String chatTitle) {
        return repo.findByChatId(chatId).orElseGet(() -> {
            ChatSettings s = ChatSettings.builder()
                    .chatId(chatId)
                    .chatTitle(chatTitle)
                    .zoneId(defaultZoneId)
                    .summaryTime(LocalTime.parse(defaultSummaryTime))
                    .build();
            return repo.save(s);
        });
    }

    @Transactional
    public ChatSettings updatePeriod(Long chatId, LocalDate start, LocalDate end) {
        ChatSettings s = repo.findByChatId(chatId).orElseThrow();
        s.setPeriodStart(start);
        s.setPeriodEnd(end);
        return s;
    }

    @Transactional
    public ChatSettings updateTimezone(Long chatId, ZoneId zoneId) {
        ChatSettings s = repo.findByChatId(chatId).orElseThrow();
        s.setZoneId(zoneId.getId());
        return s;
    }

    @Transactional
    public ChatSettings updateSummaryTime(Long chatId, LocalTime time) {
        ChatSettings s = repo.findByChatId(chatId).orElseThrow();
        s.setSummaryTime(time);
        return s;
    }

    public boolean isWithinPeriod(ChatSettings s, LocalDate date) {
        if (s.getPeriodStart() == null || s.getPeriodEnd() == null) return false;
        return ( !date.isBefore(s.getPeriodStart()) && !date.isAfter(s.getPeriodEnd()) );
    }
}
