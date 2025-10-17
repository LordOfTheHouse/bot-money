package dev.univer.shmel.service;

import dev.univer.shmel.model.ChatSettings;
import dev.univer.shmel.model.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SummaryService {
    private final PersonService personService;

    public String renderSummary(Long chatId) {
        List<Person> persons = personService.listPeople(chatId).stream()
                .filter(p -> p.getInitialAmount() != null)
                .collect(Collectors.toList());

        if (persons.isEmpty()) {
            return "Пока нет людей с заданной начальной суммой.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Итоги на ").append(LocalDate.now()).append("\n");
        for (Person p : persons.stream().sorted(Comparator.comparing(Person::getNormalizedName)).toList()) {
            BigDecimal bal = personService.balance(chatId, p);
            sb.append("• ").append(p.getName()).append(": ").append(bal).append("\n");
        }
        return sb.toString();
    }
}
