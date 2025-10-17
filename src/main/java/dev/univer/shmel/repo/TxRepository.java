package dev.univer.shmel.repo;

import dev.univer.shmel.model.Tx;
import dev.univer.shmel.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TxRepository extends JpaRepository<Tx, Long> {
    List<Tx> findAllByChatIdAndPersonOrderByCreatedAtAsc(Long chatId, Person person);
    List<Tx> findAllByChatIdAndPersonAndCreatedAtBetweenOrderByCreatedAtAsc(Long chatId, Person person, Instant start, Instant end);
    void deleteAllByPerson(Person person);
}