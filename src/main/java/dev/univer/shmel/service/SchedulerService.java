package dev.univer.shmel.service;

import dev.univer.shmel.model.ChatSettings;
import dev.univer.shmel.repo.ChatSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final ChatSettingsRepository chatRepo;
    private final ChatSettingsService settingsService;
    private final SummaryService summaryService;
    private final TelegramSender telegramSender;

    // Run every minute to check who needs a daily summary
    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        List<ChatSettings> all = chatRepo.findAll();
        for (ChatSettings s : all) {
            ZoneId zone = ZoneId.of(s.getZoneId() == null ? "UTC" : s.getZoneId());
            ZonedDateTime now = ZonedDateTime.now(zone);
            LocalDate today = now.toLocalDate();
            if (!settingsService.isWithinPeriod(s, today)) continue;

            if (s.getSummaryTime() == null) continue;
            LocalTime t = s.getSummaryTime();
            boolean isTime = now.getHour() == t.getHour() && now.getMinute() == t.getMinute();

            if (isTime) {
                if (s.getLastSummarySentOn() == null || !s.getLastSummarySentOn().isEqual(today)) {
                    String text = summaryService.renderSummary(s.getChatId());
                    try {
                        telegramSender.send(s.getChatId(), text);
                        s.setLastSummarySentOn(today);
                        chatRepo.save(s);
                    } catch (TelegramApiException e) {
                        log.error("Failed to send summary to chat {}: {}", s.getChatId(), e.getMessage());
                    }
                }
            }
        }
    }
}
