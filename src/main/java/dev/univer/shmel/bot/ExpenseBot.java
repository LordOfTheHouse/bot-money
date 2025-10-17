package dev.univer.shmel.bot;

import dev.univer.shmel.model.ChatSettings;
import dev.univer.shmel.model.Debt;
import dev.univer.shmel.model.Person;
import dev.univer.shmel.model.Tx;
import dev.univer.shmel.service.ChatSettingsService;
import dev.univer.shmel.service.PersonService;
import dev.univer.shmel.service.SummaryService;
import dev.univer.shmel.util.ParseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpenseBot {

    private final ChatSettingsService settingsService;
    private final PersonService personService;
    private final SummaryService summaryService;
    private final AbsSender sender;

    // ====== Состояния «ждём следующее сообщение» для интерактивных команд ======
    private enum Action { SETINITIAL, SETBALANCE, ADJUST, PERIOD, TIME, TZ, SPENT, SPENTALL, DELUSER, DEBT_CREATE, PAY_DEBT }
    private static class Pending {
        final Action action;
        final Long userId;
        Pending(Action action, Long userId) { this.action = action; this.userId = userId; }
    }
    private final Map<Long, Pending> pendingByChat = new ConcurrentHashMap<>();

    // ====== Регэкспы ======
    private static final String MENTION_OPT = "(?:@\\w+)?";
    private static final String AMOUNT_SIGNED = "([+-]?\\d+(?:[\\.,]\\d{1,2})?)";

    private static final String AMOUNT_POS    = "(\\d+(?:[\\.,]\\d{1,2})?)";
    private static final String OPT_DESC = "(?:\\s+(.+))?";

    private static final Pattern PERIOD  = Pattern.compile("^/period"  + MENTION_OPT + "\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{4}-\\d{2}-\\d{2})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TZ      = Pattern.compile("^/tz"      + MENTION_OPT + "\\s+([A-Za-z_]+/[A-Za-z_]+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME    = Pattern.compile("^/time"    + MENTION_OPT + "\\s+(\\d{2}:\\d{2})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SETINIT = Pattern.compile("^/setinitial"+ MENTION_OPT + "\\s+(.+?)\\s+" + AMOUNT_SIGNED + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SETBAL  = Pattern.compile("^/setbalance"+ MENTION_OPT + "\\s+(.+?)\\s+" + AMOUNT_SIGNED + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADJUST  = Pattern.compile("^/adjust"    + MENTION_OPT + "\\s+(.+?)\\s+" + AMOUNT_SIGNED + OPT_DESC + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPENT   = Pattern.compile("^/spent"     + MENTION_OPT + "\\s+(.+?)\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}[./][0-9]{2}[./][0-9]{4})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPENTALL= Pattern.compile("^/spentall"  + MENTION_OPT + "\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELUSER = Pattern.compile("^/(?:deluser|deleteuser)"+ MENTION_OPT + "\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern SETINIT_EMPTY = Pattern.compile("^/setinitial" + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SETBAL_EMPTY  = Pattern.compile("^/setbalance" + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADJUST_EMPTY  = Pattern.compile("^/adjust"     + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD_EMPTY  = Pattern.compile("^/period"     + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_EMPTY    = Pattern.compile("^/time"       + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TZ_EMPTY      = Pattern.compile("^/tz"         + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPENT_EMPTY   = Pattern.compile("^/spent"      + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPENTALL_EMPTY= Pattern.compile("^/spentall"   + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELUSER_EMPTY = Pattern.compile("^/(?:deluser|deleteuser)"+ MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern DEBT_CMD = Pattern.compile("^/debt"+MENTION_OPT+"\\s+(.+?)\\s+(.+?)\\s+"+AMOUNT_POS+OPT_DESC+"\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAY_CMD  = Pattern.compile("^/pay"+MENTION_OPT+"\\s+(.+?)\\s+(.+?)\\s+"+AMOUNT_POS+OPT_DESC+"\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBT_PHRASE = Pattern.compile("^\\s*(.+?)\\s+долж(?:ен|на|ны)\\s+(.+?)\\s+"+AMOUNT_POS+OPT_DESC+"\\s*$", Pattern.CASE_INSENSITIVE);
    // интерактивные
    private static final Pattern DEBT_EMPTY = Pattern.compile("^/debt"+MENTION_OPT+"\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAY_EMPTY  = Pattern.compile("^/pay"+MENTION_OPT+"\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern SUMMARY = Pattern.compile("^/summary" + MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HELP    = Pattern.compile("^/(start|help)"+ MENTION_OPT + "\\s*$", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter DATE_DOTS = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm");

    @EventListener
    public void onUpdate(Update update) {
        try { handle(update); } catch (Exception e) { log.error("Error processing update", e); }
    }

    private void handle(Update update) throws TelegramApiException {
        if (!update.hasMessage()) return;
        Message msg = update.getMessage();
        Chat chat = msg.getChat();
        if (chat == null) return;
        if (!msg.hasText()) return;

        Long chatId = chat.getId();
        String chatTitle = chat.getTitle();
        String type = chat.getType();
        boolean isGroup = "group".equals(type) || "supergroup".equals(type);
        if (!isGroup) return;

        ChatSettings s = settingsService.ensure(chatId, chatTitle);
        ZoneId zone = (s.getZoneId() == null)
                      ? ZoneId.systemDefault()
                      : ZoneId.of(s.getZoneId());
        String text = msg.getText().trim();
        Long fromId = msg.getFrom() != null ? msg.getFrom().getId() : null;

        // ===== если ожидаем следующее сообщение по интерактивной команде =====
        Pending pending = pendingByChat.get(chatId);
        if (pending != null) {
            if (fromId == null || !fromId.equals(pending.userId)) {
                // игнорируем ответы не от инициатора
                return;
            }
            switch (pending.action) {
                case SETINITIAL -> {
                    // РАНЬШЕ: var parsed = ParseUtil.parseNameAmountDesc(text);
                    var parsed = ParseUtil.parseInitialFlexible(text); // теперь допускаем "Имя: Сумма" и отсутствие знака
                    if (parsed == null) {
                        send(chatId, "Не понял. Введите в формате: Имя [+/-]Сумма [Описание]\n" +
                                     "Также можно: «Имя: Сумма»\nНапример: Петров +20 или Петров: 300");
                        return;
                    }
                    if (personService.findExisting(chatId, parsed.name).isPresent()) {
                        pendingByChat.remove(chatId);
                        send(chatId, "Имя уже существует: " + parsed.name + ". Используйте /adjust " + parsed.name +
                                     " <+/−Сумма> [Описание] или /setbalance " + parsed.name + " <Сумма>.");
                        return;
                    }
                    personService.setInitial(chatId, parsed.name, parsed.signedAmount);
                    pendingByChat.remove(chatId);
                    send(chatId, "Пользователь " + parsed.name + " задан, баланс установлен: " + fmtMoney(parsed.signedAmount));
                    return;
                }
                case SETBALANCE -> {
                    Matcher m = Pattern.compile("^(.+?)\\s+" + AMOUNT_SIGNED + "\\s*$").matcher(text);
                    if (!m.matches()) {
                        send(chatId, "Введите: Имя Сумма\nНапример: Петров 150");
                        return;
                    }
                    String name = m.group(1).trim();
                    BigDecimal target = new BigDecimal(m.group(2).replace(',', '.'));
                    try {
                        personService.setBalance(chatId, name, target);
                        send(chatId, "✅ Баланс для " + name + " установлен: " + fmtMoney(target));
                    } catch (IllegalArgumentException ex) {
                        send(chatId, "❌ Неизвестное имя: " + name + ". Сначала /setinitial");
                    }
                    pendingByChat.remove(chatId);
                    return;
                }
                case ADJUST -> {
                    var parsed = ParseUtil.parseNameAmountDesc(text);
                    if (parsed == null) {
                        send(chatId, "Введите: Имя +/-Сумма [Описание]\nНапример: Петров -30 Колготки");
                        return;
                    }
                    var newBal = personService.adjustIfExists(chatId, parsed.name, parsed.signedAmount, parsed.description);
                    if (newBal.isPresent()) {
                        String suffix = parsed.description == null ? "" : " — " + parsed.description;
                        send(chatId, "✅ " + parsed.name + (parsed.signedAmount.signum() >= 0 ? " +" : " ") + fmtMoney(parsed.signedAmount) + suffix +
                                     " → баланс: " + fmtMoney(newBal.get()));
                    } else {
                        send(chatId, "❌ Неизвестное имя: " + parsed.name + ". Сначала /setinitial");
                    }
                    pendingByChat.remove(chatId);
                    return;
                }
                case PERIOD -> {
                    LocalDate[] range = parsePeriodFlexible(text);
                    if (range == null) {
                        send(chatId, "Введите период в формате: YYYY-MM-DD YYYY-MM-DD или DD.MM.YYYY DD.MM.YYYY");
                        return;
                    }
                    settingsService.updatePeriod(chatId, range[0], range[1]);
                    send(chatId, "Период обновлён: " + range[0] + " — " + range[1]);
                    pendingByChat.remove(chatId);
                    return;
                }
                case TIME -> {
                    try {
                        LocalTime t = LocalTime.parse(text);
                        settingsService.updateSummaryTime(chatId, t);
                        send(chatId, "Время ежедневной сводки: " + t);
                        pendingByChat.remove(chatId);
                    } catch (Exception e) {
                        send(chatId, "Введите время в формате HH:mm (например, 23:30)");
                    }
                    return;
                }
                case TZ -> {
                    try {
                        ZoneId z = ZoneId.of(text);
                        settingsService.updateTimezone(chatId, z);
                        send(chatId, "Часовой пояс для сводок: " + z.getId());
                        pendingByChat.remove(chatId);
                    } catch (Exception e) {
                        send(chatId, "Введите часовой пояс формата Area/City (например, Europe/Moscow)");
                    }
                    return;
                }
                case SPENT -> {
                    Matcher m = Pattern.compile("^(.+?)\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}[./][0-9]{2}[./][0-9]{4})\\s*$").matcher(text);
                    if (!m.matches()) {
                        send(chatId, "Введите: Имя Дата\nДата: YYYY-MM-DD или DD.MM.YYYY");
                        return;
                    }
                    String name = m.group(1).trim();
                    LocalDate date = parseFlexibleDate(m.group(2));
                    if (date == null) { send(chatId, "Неверная дата. Пример: 2025-10-10"); return; }
                    Optional<Person> p = personService.findExisting(chatId, name);
                    if (p.isEmpty()) { send(chatId, "❌ Неизвестное имя: " + name); pendingByChat.remove(chatId); return; }
                    List<Tx> txs = personService.expensesByDate(chatId, p.get(), date, zone);
                    if (txs.isEmpty()) { send(chatId, "Расходов у " + name + " за " + date.format(DATE_DOTS) + " не найдено."); pendingByChat.remove(chatId); return; }
                    String header = "Транзакции " + date.format(DATE_DOTS) + ":";
                    String body = txs.stream()
                                     .map(t -> (t.getDescription() == null || t.getDescription().isBlank() ? "(без описания)" : t.getDescription().trim())
                                               + " -" + fmtMoney(t.getAmount().abs()))
                                     .collect(Collectors.joining("\n"));
                    send(chatId, header + "\n" + body);
                    pendingByChat.remove(chatId);
                    return;
                }
                case SPENTALL -> {
                    String name = text.trim();
                    Optional<Person> p = personService.findExisting(chatId, name);
                    if (p.isEmpty()) { send(chatId, "❌ Неизвестное имя: " + name); return; }
                    List<Tx> all = personService.expensesAll(chatId, p.get());
                    if (all.isEmpty()) { send(chatId, "Расходов у " + name + " пока нет."); pendingByChat.remove(chatId); return; }
                    Map<LocalDate, List<Tx>> byDate = all.stream().collect(Collectors.groupingBy(
                            t -> ZonedDateTime.ofInstant(t.getCreatedAt(), zone).toLocalDate(),
                            TreeMap::new, Collectors.toList()
                                                                                                ));
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<LocalDate, List<Tx>> e : byDate.entrySet()) {
                        sb.append("Транзакции ").append(e.getKey().format(DATE_DOTS)).append(":\n");
                        for (Tx t : e.getValue()) {
                            String d = (t.getDescription() == null || t.getDescription().isBlank()) ? "(без описания)" : t.getDescription().trim();
                            sb.append(d).append(" -").append(fmtMoney(t.getAmount().abs())).append("\n");
                        }
                    }
                    send(chatId, sb.toString().trim());
                    pendingByChat.remove(chatId);
                    return;
                }
                case DELUSER -> {
                    String name = text.trim();
                    if (fromId == null || !isAdmin(chatId, fromId)) {
                        send(chatId, "❌ Только администраторы чата могут удалять пользователей.");
                        pendingByChat.remove(chatId);
                        return;
                    }
                    boolean removed = personService.deletePerson(chatId, name);
                    send(chatId, removed
                                 ? "🗑 Пользователь «" + name + "» и все его транзакции удалены."
                                 : "❌ Не найден пользователь: " + name);
                    pendingByChat.remove(chatId);
                    return;
                }
                case DEBT_CREATE -> {
                    Matcher m = Pattern.compile("^(.+?)\\s+(.+?)\\s+"+AMOUNT_POS+OPT_DESC+"\\s*$").matcher(text);
                    if (!m.matches()) { send(chatId,"Введите: Должник Кредитор Сумма [Описание]"); return; }
                    try {
                        BigDecimal sum = new BigDecimal(m.group(3).replace(',', '.'));
                        var debt = personService.createDebt(chatId, m.group(1).trim(), m.group(2).trim(), sum, opt(m,4));
                        send(chatId, "Долг записан: " + debt.getDebtor().getName() + " → " + debt.getCreditor().getName()
                                     + " " + fmt(sum) + (debt.getDescription()==null?"":" — "+debt.getDescription()));
                    } catch (IllegalArgumentException ex) {
                        send(chatId, "❌ " + ex.getMessage());
                    }
                    pendingByChat.remove(chatId); return;
                }
                case PAY_DEBT -> {
                    Matcher m = Pattern.compile("^(.+?)\\s+(.+?)\\s+"+AMOUNT_POS+OPT_DESC+"\\s*$").matcher(text);
                    if (!m.matches()) { send(chatId,"Введите: Должник Кредитор Сумма [Описание]"); return; }
                    try {
                        BigDecimal sum = new BigDecimal(m.group(3).replace(',', '.'));
                        var res = personService.payDebt(chatId, m.group(1).trim(), m.group(2).trim(), sum, opt(m,4));
                        if (res.applied().signum()==0) {
                            send(chatId, "Открытых долгов не найдено.");
                        } else {
                            send(chatId, "Погашено: " + fmt(res.applied()) + (res.stillOwe().signum()>0 ? ("; остаток долга: "+fmt(res.stillOwe())) : "; долг закрыт ✅"));
                        }
                    } catch (IllegalArgumentException ex) {
                        send(chatId, "❌ " + ex.getMessage());
                    }
                    pendingByChat.remove(chatId); return;
                }
            }
        }

        // ===== Команды с аргументами — выполняем сразу; без аргументов — просим ввести и ждём следующее сообщение =====
        if (HELP.matcher(text).matches()) { send(chatId, helpText(s)); return; }
        if (SUMMARY.matcher(text).matches()) { send(chatId, summaryService.renderSummary(chatId)); return; }

        if (DEBT_EMPTY.matcher(text).matches()) { ask(chatId, fromId, Action.DEBT_CREATE, "Введите: Должник Кредитор Сумма [Описание]"); return; }
        Matcher dm = DEBT_CMD.matcher(text);
        if (dm.matches()) {
            try {
                BigDecimal sum = new BigDecimal(dm.group(3).replace(',', '.'));
                var debt = personService.createDebt(chatId, dm.group(1).trim(), dm.group(2).trim(), sum, opt(dm,4));
                send(chatId, "Долг записан: " + debt.getDebtor().getName() + " → " + debt.getCreditor().getName()
                             + " " + fmt(sum) + (debt.getDescription()==null?"":" — "+debt.getDescription()));
            } catch (IllegalArgumentException ex) { send(chatId, "❌ " + ex.getMessage()); }
            return;
        }

        if (PAY_EMPTY.matcher(text).matches()) { ask(chatId, fromId, Action.PAY_DEBT, "Введите: Должник Кредитор Сумма [Описание]"); return; }
        Matcher pm = PAY_CMD.matcher(text);
        if (pm.matches()) {
            try {
                BigDecimal sum = new BigDecimal(pm.group(3).replace(',', '.'));
                var res = personService.payDebt(chatId, pm.group(1).trim(), pm.group(2).trim(), sum, opt(pm,4));
                if (res.applied().signum()==0) { send(chatId, "Открытых долгов не найдено."); }
                else { send(chatId, "Погашено: " + fmt(res.applied()) + (res.stillOwe().signum()>0 ? ("; остаток долга: "+fmt(res.stillOwe())) : "; долг закрыт ✅")); }
            } catch (IllegalArgumentException ex) { send(chatId, "❌ " + ex.getMessage()); }
            return;
        }

        if (text.matches("^/debts"+MENTION_OPT+"\\s*$")) {
            List<Debt> all = personService.listOpenDebts( chatId );
            if (all.isEmpty()) { send(chatId, "Открытых долгов нет."); return; }
            send(chatId, renderDebts(all)); return;
        }
        Matcher debFor = Pattern.compile("^/debts"+MENTION_OPT+"\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE).matcher(text);
        if (debFor.matches()) {
            List<Debt> all = personService.listOpenDebtsFor(chatId, debFor.group(1).trim());
            if (all.isEmpty()) { send(chatId, "Открытых долгов для " + debFor.group(1).trim() + " нет."); return; }
            send(chatId, renderDebts(all)); return;
        }

        // ===== естественная фраза "А должен Б N [desc]" =====
        Matcher phrase = DEBT_PHRASE.matcher(text);
        if (phrase.matches()) {
            try {
                BigDecimal sum = new BigDecimal(phrase.group(3).replace(',', '.'));
                var debt = personService.createDebt(chatId, phrase.group(1).trim(), phrase.group(2).trim(), sum, opt(phrase,4));
                send(chatId, "Долг записан: " + debt.getDebtor().getName() + " → " + debt.getCreditor().getName()
                             + " " + fmt(sum) + (debt.getDescription()==null?"":" — "+debt.getDescription()));
            } catch (IllegalArgumentException ex) { send(chatId, "❌ " + ex.getMessage()); }
            return;
        }

        if (PERIOD_EMPTY.matcher(text).matches()) { startPending(chatId, fromId, Action.PERIOD,
                                                                 "Введите период: YYYY-MM-DD YYYY-MM-DD (или DD.MM.YYYY DD.MM.YYYY)"); return; }
        pm = PERIOD.matcher(text);
        if (pm.matches()) {
            LocalDate from = LocalDate.parse(pm.group(1));
            LocalDate to   = LocalDate.parse(pm.group(2));
            settingsService.updatePeriod(chatId, from, to);
            send(chatId, "Период обновлён: " + from + " — " + to);
            return;
        }

        if (TIME_EMPTY.matcher(text).matches()) { startPending(chatId, fromId, Action.TIME,
                                                               "Введите время ежедневной сводки (HH:mm), например 23:30"); return; }
        var tm = TIME.matcher(text);
        if (tm.matches()) {
            LocalTime t = LocalTime.parse(tm.group(1));
            settingsService.updateSummaryTime(chatId, t);
            send(chatId, "Время ежедневной сводки: " + t);
            return;
        }

        if (TZ_EMPTY.matcher(text).matches()) { startPending(chatId, fromId, Action.TZ,
                                                             "Введите часовой пояс формата Area/City, например Europe/Moscow"); return; }
        var tzm = TZ.matcher(text);
        if (tzm.matches()) {
            ZoneId z = ZoneId.of(tzm.group(1));
            settingsService.updateTimezone(chatId, z);
            send(chatId, "Часовой пояс для сводок: " + z.getId());
            return;
        }

        if (SETINIT_EMPTY.matcher(text).matches()) {
            startPending(chatId, fromId, Action.SETINITIAL,
                         "Задайте баланс для нового пользователя.\n" +
                         "Формат: Имя [+/-]Сумма [Описание]\n" +
                         "Можно писать с двоеточием: «Имя: Сумма»\n" +
                         "Например: Петров +20 или Петров: 300");
            return;
        }
        var sim = SETINIT.matcher(text);
        if (sim.matches()) {
            String name = sim.group(1).trim();
            BigDecimal init = new BigDecimal(sim.group(2).replace(',', '.'));
            if (personService.findExisting(chatId, name).isPresent()) {
                send(chatId, "Имя уже существует: " + name + ". Используйте /adjust " + name + " <+/−Сумма> [Описание] или /setbalance " + name + " <Сумма>.");
                return;
            }
            personService.setInitial(chatId, name, init);
            send(chatId, "Пользователь " + name + " задан, баланс установлен: " + fmtMoney(init));
            return;
        }

        if (SETBAL_EMPTY.matcher(text).matches()) { startPending(chatId, fromId, Action.SETBALANCE,
                                                                 "Введите: Имя Сумма\nНапример: Петров 150"); return; }
        var sbm = SETBAL.matcher(text);
        if (sbm.matches()) {
            String name = sbm.group(1).trim();
            BigDecimal target = new BigDecimal(sbm.group(2).replace(',', '.'));
            try {
                personService.setBalance(chatId, name, target);
                send(chatId, "✅ Баланс для " + name + " установлен: " + fmtMoney(target));
            } catch (IllegalArgumentException ex) {
                send(chatId, "❌ Неизвестное имя: " + name + ". Сначала /setinitial");
            }
            return;
        }

        if (ADJUST_EMPTY.matcher(text).matches()) { startPending(chatId, fromId, Action.ADJUST,
                                                                 "Введите: Имя +/-Сумма [Описание]\nНапример: Петров -30 Колготки"); return; }
        var ajm = ADJUST.matcher(text);
        if (ajm.matches()) {
            String name = ajm.group(1).trim();
            BigDecimal delta = new BigDecimal(ajm.group(2).replace(',', '.'));
            String desc = ajm.groupCount() >= 3 ? ajm.group(3) : null;
            var newBal = personService.adjustIfExists(chatId, name, delta, desc);
            if (newBal.isPresent()) {
                String suffix = (desc == null || desc.isBlank()) ? "" : " — " + desc.trim();
                send(chatId, "✅ " + name + (delta.signum() >= 0 ? " +" : " ") + fmtMoney(delta) + suffix +
                             " → баланс: " + fmtMoney(newBal.get()));
            } else {
                send(chatId, "❌ Неизвестное имя: " + name + ". Сначала /setinitial");
            }
            return;
        }

        if (SPENT_EMPTY.matcher(text).matches()) { startPending(chatId, fromId, Action.SPENT,
                                                                "Введите: Имя Дата\nДата: YYYY-MM-DD или DD.MM.YYYY"); return; }
        var sp = SPENT.matcher(text);
        if (sp.matches()) {
            String name = sp.group(1).trim();
            Optional<Person> p = personService.findExisting(chatId, name);
            if (p.isEmpty()) { send(chatId, "❌ Неизвестное имя: " + name); return; }

            LocalDate date = parseFlexibleDate(sp.group(2));
            if (date == null) {
                send(chatId, "Неверная дата. Используйте YYYY-MM-DD или DD.MM.YYYY");
                return;
            }

            // Сначала — только расходы
            List<Tx> expenses = personService.expensesByDate(chatId, p.get(), date, zone);
            if (!expenses.isEmpty()) {
                String header = "Транзакции " + date.format(DATE_DOTS) + ":";
                String body = expenses.stream()
                                      .map(t -> (t.getDescription() == null || t.getDescription().isBlank() ? "(без описания)" : t.getDescription().trim())
                                                + " -" + fmtMoney(t.getAmount().abs()))
                                      .collect(Collectors.joining("\n"));
                send(chatId, header + "\n" + body);
                return;
            }

            // Если расходов нет — смотрим любые операции за этот день и объясняем ситуацию
            List<Tx> all = personService.transactionsByDate(chatId, p.get(), date, zone);
            if (all.isEmpty()) {
                String tzHint = (s.getZoneId() == null)
                                ? "\n(Подсказка: часовой пояс по умолчанию — " + ZoneId.systemDefault().getId() + ". Задайте свой: /tz Europe/Moscow)"
                                : "";
                send(chatId, "Расходов у " + name + " за " + date.format(DATE_DOTS) + " не найдено." + tzHint);
                return;
            }

            // Были операции, но не расходы — покажем их (пополнения/нулевые)
            String header = "Расходов нет. Операции " + date.format(DATE_DOTS) + ":";
            String body = all.stream()
                             .map(t -> {
                                 String d = (t.getDescription() == null || t.getDescription().isBlank()) ? "(без описания)" : t.getDescription().trim();
                                 String sign = t.getAmount().signum() >= 0 ? "+" : "-";
                                 return d + " " + sign + fmtMoney(t.getAmount().abs());
                             })
                             .collect(Collectors.joining("\n"));
            send(chatId, header + "\n" + body);
            return;
        }

        if (SPENTALL_EMPTY.matcher(text).matches()) { startPending(chatId, fromId, Action.SPENTALL,
                                                                   "Введите имя пользователя, чтобы показать все его расходы по дням"); return; }
        var spa = SPENTALL.matcher(text);
        if (spa.matches()) {
            String name = spa.group(1).trim();
            Optional<Person> p = personService.findExisting(chatId, name);
            if (p.isEmpty()) { send(chatId, "❌ Неизвестное имя: " + name); return; }
            List<Tx> all = personService.expensesAll(chatId, p.get());
            if (all.isEmpty()) { send(chatId, "Расходов у " + name + " пока нет."); return; }
            Map<LocalDate, List<Tx>> byDate = all.stream().collect(Collectors.groupingBy(
                    t -> ZonedDateTime.ofInstant(t.getCreatedAt(), zone).toLocalDate(),
                    TreeMap::new, Collectors.toList()
                                                                                        ));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<LocalDate, List<Tx>> e : byDate.entrySet()) {
                sb.append("Транзакции ").append(e.getKey().format(DATE_DOTS)).append(":\n");
                for (Tx t : e.getValue()) {
                    String d = (t.getDescription() == null || t.getDescription().isBlank()) ? "(без описания)" : t.getDescription().trim();
                    sb.append(d).append(" -").append(fmtMoney(t.getAmount().abs())).append("\n");
                }
            }
            send(chatId, sb.toString().trim());
            return;
        }

        if (DELUSER_EMPTY.matcher(text).matches()) {
            startPending(chatId, fromId, Action.DELUSER, "Введите имя пользователя, которого нужно удалить");
            return;
        }
        dm = DELUSER.matcher(text);
        if (dm.matches()) {
            String name = dm.group(1).trim();
            if (fromId == null || !isAdmin(chatId, fromId)) {
                send(chatId, "❌ Только администраторы чата могут удалять пользователей.");
                return;
            }
            boolean removed = personService.deletePerson(chatId, name);
            send(chatId, removed ? "🗑 Пользователь «" + name + "» и все его транзакции удалены."
                                 : "❌ Не найден пользователь: " + name);
            return;
        }

        // ===== Свободный текст «Имя +/-Сумма [Описание]» (только для существующих) =====
        var parsed = ParseUtil.parseNameAmountDesc(text);
        if (parsed != null) {
            var newBal = personService.adjustIfExists(chatId, parsed.name, parsed.signedAmount, parsed.description);
            if (newBal.isPresent()) {
                String suffix = parsed.description == null ? "" : " — " + parsed.description;
                send(chatId, "✅ " + parsed.name + (parsed.signedAmount.signum() >= 0 ? " +" : " ") + fmtMoney(parsed.signedAmount) +
                             suffix + " → баланс: " + fmtMoney(newBal.get()));
            } else {
                send(chatId, "❌ Неизвестное имя: " + parsed.name + ". Сначала /setinitial");
            }
        }
    }

    private String renderDebts(List<Debt> all) {
        StringBuilder sb = new StringBuilder("Открытые долги:\n");
        for (Debt d : all) {
            sb.append("• ").append(d.getDebtor().getName()).append(" → ").append(d.getCreditor().getName())
              .append(" ").append(fmt(d.getRemaining()));
            if (d.getDescription()!=null && !d.getDescription().isBlank()) sb.append(" — ").append(d.getDescription().trim());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String fmt(BigDecimal x){ return x.stripTrailingZeros().toPlainString(); }

    private void startPending(Long chatId, Long userId, Action action, String prompt) throws TelegramApiException {
        pendingByChat.put(chatId, new Pending(action, userId));
        send(chatId, prompt + "\n(жду ваше следующее сообщение)");
    }

    private void ask(Long chatId, Long fromId, Action a, String prompt) throws TelegramApiException {
        pendingByChat.put(chatId, new Pending(a, fromId));
        send(chatId, prompt + "\n(жду ваше следующее сообщение)");
    }

    private static String opt(Matcher m, int group) {
        return (m.groupCount() >= group && m.group(group) != null && !m.group(group).isBlank()) ? m.group(group).trim() : null;
    }

    private void send(Long chatId, String text) throws TelegramApiException {
        SendMessage sm = SendMessage.builder().chatId(chatId.toString()).text(text).build();
        sender.execute(sm);
    }

    private static String fmtMoney(BigDecimal x) { return x.stripTrailingZeros().toPlainString(); }

    private static LocalDate parseFlexibleDate(String raw) {
        try {
            if (raw.contains(".")) {
                return LocalDate.parse(raw.replace('/', '.').replace('-', '.'), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } else {
                return LocalDate.parse(raw); // ISO
            }
        } catch (Exception e) { return null; }
    }
    private static LocalDate[] parsePeriodFlexible(String raw) {
        String[] p = raw.trim().split("\\s+");
        if (p.length != 2) return null;
        LocalDate a = parseFlexibleDate(p[0]);
        LocalDate b = parseFlexibleDate(p[1]);
        if (a == null || b == null) return null;
        return a.isBefore(b) || a.isEqual(b) ? new LocalDate[]{a,b} : new LocalDate[]{b,a};
    }

    private boolean isAdmin(Long chatId, Long userId) {
        try {
            GetChatMember req = GetChatMember.builder().chatId(chatId.toString()).userId(userId.longValue()).build();
            ChatMember cm = sender.execute(req);
            String status = cm.getStatus();
            return "administrator".equalsIgnoreCase(status) || "creator".equalsIgnoreCase(status) || "owner".equalsIgnoreCase(status);
        } catch (TelegramApiException e) {
            log.warn("Failed to check admin rights: chat={}, user={}, err={}", chatId, userId, e.getMessage());
            return false;
        }
    }

    private String helpText(ChatSettings s) {
        String tz = s.getZoneId() == null ? "UTC" : s.getZoneId();
        String time = s.getSummaryTime() == null ? "23:59" : s.getSummaryTime().toString();
        return String.join("\n", List.of(
                "Привет! Я бот учёта трат.",
                "",
                "Сообщения в чате:",
                "• Имя +/-Сумма [Описание]  (например: Петров -20 Колготки) — изменю баланс, сохраню описание.",
                "",
                "Команды (если ввести без аргументов — подскажу и дождусь следующего сообщения):",
                "/setinitial [Имя Сумма] — задать начальную сумму",
                "/adjust [Имя +/-Сумма [Описание]] — прибавить/вычесть",
                "/setbalance [Имя Сумма] — установить текущий баланс",
                "/spent [Имя Дата] — расходы за дату (YYYY-MM-DD или DD.MM.YYYY)",
                "/spentall [Имя] — все расходы по дням",
                "/deluser [Имя] — удалить пользователя (только админы)",
                "/summary — показать сводку сейчас",
                "/period [from to] — задать период сводок",
                "/time [HH:mm] — время сводки",
                "/tz [Area/City] — часовой пояс",
                "",
                "Текущие настройки: TZ=" + tz + ", daily at " + time
                                        ));
    }
}
