package dev.univer.shmel.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(indexes = {
        @Index(name="idx_tx_chat_person_time", columnList = "chatId, person_id, createdAt")
})
public class Tx {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tx_person"))
    @OnDelete(action = OnDeleteAction.CASCADE) // для новых схем создаст ON DELETE CASCADE
    private Person person;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    private Instant createdAt;

    @Column(length = 512)
    private String description;
}