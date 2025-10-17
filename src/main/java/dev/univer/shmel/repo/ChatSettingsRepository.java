package dev.univer.shmel.repo;

import dev.univer.shmel.model.ChatSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSettingsRepository extends JpaRepository<ChatSettings, Long> {
    Optional<ChatSettings> findByChatId(Long chatId);
}
