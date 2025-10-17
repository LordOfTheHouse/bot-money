package dev.univer.shmel.repo;

import dev.univer.shmel.model.Debt;
import dev.univer.shmel.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findAllByChatIdAndRemainingGreaterThanOrderByCreatedAtAsc(Long chatId, BigDecimal zero);
    List<Debt> findAllByChatIdAndDebtorAndRemainingGreaterThanOrderByCreatedAtAsc(Long chatId, Person debtor, BigDecimal zero);
    List<Debt> findAllByChatIdAndCreditorAndRemainingGreaterThanOrderByCreatedAtAsc(Long chatId, Person creditor, BigDecimal zero);
    List<Debt> findAllByChatIdAndDebtorAndCreditorAndRemainingGreaterThanOrderByCreatedAtAsc(Long chatId, Person debtor, Person creditor, BigDecimal zero);
}