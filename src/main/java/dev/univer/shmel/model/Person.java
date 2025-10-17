package dev.univer.shmel.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(indexes = {
    @Index(name="idx_person_chat_name", columnList = "chatId, normalizedName", unique = true)
})
public class Person {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;

    private String name; // original as typed (first seen)
    private String normalizedName; // upper-case trimmed for uniqueness

    @Column(precision = 19, scale = 2)
    private BigDecimal initialAmount;
}
