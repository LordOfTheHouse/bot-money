package dev.univer.shmel.model;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"chatId"}))
public class ChatSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String chatTitle;

    // Period when daily summaries are active (inclusive)
    private LocalDate periodStart;
    private LocalDate periodEnd;

    // Time (HH:mm) when summary should be sent
    private LocalTime summaryTime;

    // Which timezone to interpret "end of day"
    private String zoneId;

    // Last date we sent a daily summary in that zone
    private LocalDate lastSummarySentOn;
}
