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
    // "Имя +300 [Описание]" или "Петров -30 Колготки"
    private static final String NAME = "(?<name>[A-Za-zА-ЯЁа-яё][A-Za-zА-ЯЁа-яё\\- _]{0,49})";
    private static final String SIGN_AMOUNT = "(?<sign>[+\\-−—–])\\s*(?<amount>\\d+(?:[\\.,]\\d{1,2})?)";
    private static final String OPT_DESC = "(?:\\s+(?<desc>.+))?";
    private static final Pattern LINE = Pattern.compile("^\\s*" + NAME + "\\s+" + SIGN_AMOUNT + OPT_DESC + "\\s*$");

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

    public static String normalizeName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
