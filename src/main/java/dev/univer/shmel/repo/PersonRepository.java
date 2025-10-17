package dev.univer.shmel.repo;

import dev.univer.shmel.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByChatIdAndNormalizedName(Long chatId, String normalizedName);
    List<Person> findAllByChatIdOrderByNormalizedNameAsc(Long chatId);
}
