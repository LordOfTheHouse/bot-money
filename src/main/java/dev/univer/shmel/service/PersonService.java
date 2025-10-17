package dev.univer.shmel.service;

import dev.univer.shmel.model.*;
import dev.univer.shmel.repo.*;
import dev.univer.shmel.util.ParseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import dev.univer.shmel.model.Person;
import dev.univer.shmel.model.Tx;
import dev.univer.shmel.repo.PersonRepository;
import dev.univer.shmel.repo.TxRepository;
import dev.univer.shmel.util.ParseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PersonService {
    private final PersonRepository personRepository;
    private final TxRepository txRepository;
    private final DebtRepository debtRepository;

    @Transactional
    public Person setInitial(Long chatId, String name, BigDecimal initial) {
        String norm = ParseUtil.normalizeName(name);
        Optional<Person> existing = personRepository.findByChatIdAndNormalizedName(chatId, norm);
        Person p = existing.orElseGet(() -> Person.builder()
                                                  .chatId(chatId)
                                                  .name(name.trim())
                                                  .normalizedName(norm)
                                                  .build());
        p.setInitialAmount(initial);
        return personRepository.save(p);
    }

    @Transactional
    public Optional<BigDecimal> adjustIfExists(Long chatId, String name, BigDecimal delta, String description) {
        String norm = ParseUtil.normalizeName(name);
        Optional<Person> opt = personRepository.findByChatIdAndNormalizedName(chatId, norm);
        if (opt.isEmpty()) return Optional.empty();

        Person p = opt.get();
        Tx tx = Tx.builder()
                  .chatId(chatId)
                  .person(p)
                  .amount(delta)
                  .description(description)
                  .createdAt(Instant.now())
                  .build();
        txRepository.save(tx);

        return Optional.of(balance(chatId, p));
    }

    @Transactional
    public BigDecimal setBalance(Long chatId, String name, BigDecimal target) {
        String norm = ParseUtil.normalizeName(name);
        Person p = personRepository.findByChatIdAndNormalizedName(chatId, norm)
                                   .orElseThrow(() -> new IllegalArgumentException("Неизвестное имя: " + name));

        BigDecimal current = balance(chatId, p);
        BigDecimal delta = target.subtract(current);
        if (delta.signum() != 0) {
            Tx correction = Tx.builder()
                              .chatId(chatId)
                              .person(p)
                              .amount(delta)
                              .description("Корректировка до целевого баланса")
                              .createdAt(Instant.now())
                              .build();
            txRepository.save(correction);
        }
        return target;
    }

    public BigDecimal balance(Long chatId, Person p) {
        List<Tx> all = txRepository.findAllByChatIdAndPersonOrderByCreatedAtAsc(chatId, p);
        BigDecimal sum = p.getInitialAmount() == null ? BigDecimal.ZERO : p.getInitialAmount();
        for (Tx t : all) sum = sum.add(t.getAmount());
        return sum;
    }

    public List<Person> listPeople(Long chatId) {
        return personRepository.findAllByChatIdOrderByNormalizedNameAsc(chatId);
    }

    public Optional<Person> findExisting(Long chatId, String name) {
        return personRepository.findByChatIdAndNormalizedName(chatId, ParseUtil.normalizeName(name));
    }

    public List<Tx> transactionsByDate(Long chatId, Person p, LocalDate date, ZoneId zone) {
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        return txRepository.findAllByChatIdAndPersonAndCreatedAtBetweenOrderByCreatedAtAsc(chatId, p, start, end);
    }

    public List<Tx> expensesByDate(Long chatId, Person p, LocalDate date, ZoneId zone) {
        List<Tx> list = transactionsByDate(chatId, p, date, zone);
        return list.stream().filter(t -> t.getAmount().signum() < 0).toList();
    }

    public List<Tx> expensesAll(Long chatId, Person p) {
        List<Tx> list = txRepository.findAllByChatIdAndPersonOrderByCreatedAtAsc(chatId, p);
        return list.stream().filter(t -> t.getAmount().signum() < 0).toList();
    }

    // ===================== DEBTS =====================

    @Transactional
    public Debt createDebt(Long chatId, String debtorName, String creditorName, BigDecimal amount, String desc) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Сумма долга должна быть > 0");
        String dNorm = ParseUtil.normalizeName(debtorName);
        String cNorm = ParseUtil.normalizeName(creditorName);
        if (dNorm.equals(cNorm)) throw new IllegalArgumentException("Должник и кредитор не могут совпадать");

        Person debtor = personRepository.findByChatIdAndNormalizedName(chatId, dNorm)
                                        .orElseThrow(() -> new IllegalArgumentException("Неизвестный должник: " + debtorName));
        Person creditor = personRepository.findByChatIdAndNormalizedName(chatId, cNorm)
                                          .orElseThrow(() -> new IllegalArgumentException("Неизвестный кредитор: " + creditorName));

        Debt debt = Debt.builder()
                        .chatId(chatId)
                        .debtor(debtor)
                        .creditor(creditor)
                        .amount(amount)
                        .remaining(amount)
                        .description(desc)
                        .createdAt(Instant.now())
                        .build();
        return debtRepository.save(debt);
    }

    /** Погасить (полностью/частично) долг между A->B, возвращает фактически погашенную сумму и остаток. */
    @Transactional
    public PaymentResult payDebt(Long chatId, String debtorName, String creditorName, BigDecimal pay, String desc) {
        if (pay.signum() <= 0) throw new IllegalArgumentException("Сумма платежа должна быть > 0");

        Person debtor = personRepository.findByChatIdAndNormalizedName(chatId, ParseUtil.normalizeName(debtorName))
                                        .orElseThrow(() -> new IllegalArgumentException("Неизвестный должник: " + debtorName));
        Person creditor = personRepository.findByChatIdAndNormalizedName(chatId, ParseUtil.normalizeName(creditorName))
                                          .orElseThrow(() -> new IllegalArgumentException("Неизвестный кредитор: " + creditorName));

        List<Debt> open = debtRepository.findAllByChatIdAndDebtorAndCreditorAndRemainingGreaterThanOrderByCreatedAtAsc(
                chatId, debtor, creditor, BigDecimal.ZERO);

        BigDecimal remainingToApply = pay;
        BigDecimal applied = BigDecimal.ZERO;

        for (Debt d : open) {
            if (remainingToApply.signum() <= 0) break;
            BigDecimal take = d.getRemaining().min(remainingToApply);
            d.setRemaining(d.getRemaining().subtract(take));
            if (d.getRemaining().signum() == 0) d.setClosedAt(Instant.now());
            debtRepository.save(d);

            remainingToApply = remainingToApply.subtract(take);
            applied = applied.add(take);
        }

        if (applied.signum() > 0) {
            // проводим движение по балансам
            Instant now = Instant.now();
            txRepository.save(Tx.builder()
                                .chatId(chatId).person(debtor).amount(applied.negate())
                                .description((desc == null || desc.isBlank())
                                             ? ("Погашение долга " + creditor.getName())
                                             : desc)
                                .createdAt(now).build());
            txRepository.save(Tx.builder()
                                .chatId(chatId).person(creditor).amount(applied)
                                .description((desc == null || desc.isBlank())
                                             ? ("Поступление по долгу от " + debtor.getName())
                                             : desc)
                                .createdAt(now).build());
        }

        BigDecimal stillOwe = open.stream()
                                  .map(Debt::getRemaining)
                                  .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PaymentResult(applied, stillOwe.max(BigDecimal.ZERO));
    }

    public record PaymentResult(BigDecimal applied, BigDecimal stillOwe) {}

    public List<Debt> listOpenDebts( Long chatId ) {
        return debtRepository.findAllByChatIdAndRemainingGreaterThanOrderByCreatedAtAsc(chatId, BigDecimal.ZERO);
    }

    public List<Debt> listOpenDebtsFor(Long chatId, String name) {
        Optional<Person> p = findExisting(chatId, name);
        if (p.isEmpty()) return Collections.emptyList();

        List<Debt> asDebtor = debtRepository.findAllByChatIdAndDebtorAndRemainingGreaterThanOrderByCreatedAtAsc(
                chatId, p.get(), BigDecimal.ZERO);
        List<Debt> asCreditor = debtRepository.findAllByChatIdAndCreditorAndRemainingGreaterThanOrderByCreatedAtAsc(
                chatId, p.get(), BigDecimal.ZERO);

        List<Debt> all = new ArrayList<>( asDebtor.size() + asCreditor.size());
        all.addAll(asDebtor);
        all.addAll(asCreditor);
        all.sort(Comparator.comparing(Debt::getCreatedAt));
        return all;
    }

    @Transactional
    public boolean deletePerson(Long chatId, String name) {
        String norm = ParseUtil.normalizeName(name);
        Optional<Person> opt = personRepository.findByChatIdAndNormalizedName(chatId, norm);
        if (opt.isEmpty()) return false;

        Person p = opt.get();

        // 1) удаляем все транзакции этого person и форсируем flush,
        // чтобы FK больше не мешал
        txRepository.deleteAllByPerson(p);
        txRepository.flush();

        // 2) удаляем пользователя и флешим
        personRepository.delete(p);
        personRepository.flush();

        return true;
    }
}