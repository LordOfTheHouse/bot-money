package dev.univer.shmel.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(indexes = {
        @Index(name="idx_debt_chat_open", columnList = "chatId, remaining"),
        @Index(name="idx_debt_pair", columnList = "chatId, debtor_id, creditor_id")
})
public class Debt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Person debtor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Person creditor;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;        // исходная сумма

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal remaining;     // остаток к погашению

    @Column(length = 512)
    private String description;       // необязательное описание

    private Instant createdAt;

    private Instant closedAt;
}