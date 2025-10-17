package dev.univer.shmel.util;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseUtil {
    // "Имя +/-Сумма [Описание]"
    private static final String NAME = "(?<name>[A-Za-zА-ЯЁа-яё][A-Za-zА-ЯЁа-яё\\- _]{0,49})";
    private static final String SIGN_AMOUNT = "(?<sign>[+\\-−—–])\\s*(?<amount>\\d+(?:[\\.,]\\d{1,2})?)";
    private static final String OPT_DESC = "(?:\\s+(?<desc>.+))?";
    private static final Pattern LINE = Pattern.compile("^\\s*" + NAME + "\\s+" + SIGN_AMOUNT + OPT_DESC + "\\s*$");

    // ИНИЦИАЛИЗАЦИЯ: допускаем "Имя [+/-]?Сумма [Описание]" и разделители после имени ":" или "-" / "—"
    // Примеры: "Валера +285.12", "Валера 285.12", "Валера: +285.12", "Валера:285.12", "Валера -100 Одежда"
    private static final Pattern INIT_LINE = Pattern.compile(
            "^\\s*" + NAME + "\\s*(?::|[-—–])?\\s*(?<sign>[+\\-−—–])?\\s*(?<amount>\\d+(?:[\\.,]\\d{1,2})?)" + OPT_DESC + "\\s*$"
                                                            );

    public static class LineMatch {
        public final String name;
        public final BigDecimal signedAmount;
        public final String description; // может быть null
        public LineMatch(String name, BigDecimal signedAmount, String description) {
            this.name = name;
            this.signedAmount = signedAmount;
            this.description = (description == null || description.isBlank()) ? null : description.trim();
        }
    }

    /** Общий разбор "Имя +/-Сумма [Описание]" — знак обязателен. */
    public static LineMatch parseNameAmountDesc(String text) {
        Matcher m = LINE.matcher(text);
        if (!m.matches()) return null;
        String name = m.group("name").trim();
        String sign = m.group("sign");
        String amountStr = m.group("amount").replace(',', '.');
        BigDecimal amount = new BigDecimal(amountStr);
        if (sign.equals("-") || sign.equals("−") || sign.equals("—") || sign.equals("–")) {
            amount = amount.negate();
        }
        String desc = m.group("desc");
        return new LineMatch(name, amount, desc);
    }

    /** Разбор для инициализации — знак можно не писать, допускаем двоеточие/тире после имени. */
    public static LineMatch parseInitialFlexible(String text) {
        Matcher m = INIT_LINE.matcher(text);
        if (!m.matches()) return null;
        String name = m.group("name").trim();
        String sign = m.group("sign"); // может быть null
        String amountStr = m.group("amount").replace(',', '.');
        BigDecimal amount = new BigDecimal(amountStr);
        if (sign != null && (sign.equals("-") || sign.equals("−") || sign.equals("—") || sign.equals("–"))) {
            amount = amount.negate();
        }
        String desc = m.group("desc");
        return new LineMatch(name, amount, desc);
    }

    public static String normalizeName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
