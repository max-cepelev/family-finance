package com.realbyte.money.rumod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text rules for push notifications of Russian banks. Pure Java so it can be tested off-device.
 *
 * Input is the notification text the way Money Manager's NotificationListener assembles it:
 * ticker, title and body joined by spaces (the ticker is often the literal "null").
 */
public final class RuBankRules {

    public static final String TBANK = "com.idamob.tinkoff.android";
    public static final String SBER = "ru.sberbankmobile";
    public static final String RAIF = "ru.raiffeisennews";

    public static final class Match {
        public final String pkg;
        public final boolean income;
        /** Absolute amount, dot as decimal separator, e.g. "14025.10". */
        public final String amount;
        public final String payee;
        /** Last 4 digits of card/account if present, else "". */
        public final String card;

        Match(String pkg, boolean income, String amount, String payee, String card) {
            this.pkg = pkg;
            this.income = income;
            this.amount = amount;
            this.payee = payee;
            this.card = card;
        }

        @Override
        public String toString() {
            return pkg + " " + (income ? "+" : "-") + amount + " [" + payee + "] card=" + card;
        }
    }

    private static final String AMT = "(\\d{1,3}(?: \\d{3})+|\\d+)(?:[.,](\\d{1,2}))?";
    private static final String CUR = "(?:₽|RUB|руб\\.?|р\\.)";

    // ➕ 10 ₽ по СБП от ИМЯ Ф. 💳 10 ₽ •• 2824
    private static final Pattern SBER_P = Pattern.compile(
            "([➕➖])\\s*" + AMT + "\\s*" + CUR + "\\s*(.*?)\\s*(?:💳|$)");
    private static final Pattern SBER_CARD = Pattern.compile("••\\s*(\\d{4})");

    // Заплатили картой *5790 - 17.00 ₽ в MARMALATO 086. Теперь на карте 14 025.10 ₽
    private static final Pattern RAIF_P = Pattern.compile(
            "\\*(\\d{4})\\s*([+-])\\s*" + AMT + "\\s*" + CUR + "\\s*(.*?)(?=\\s*[Тт]еперь на|$)");

    // Райффайзенбанк Пополнение на 10 ₽, счет RUB. Имя Ф. Доступно 10 ₽
    // (old SMS) Оплата СБП, счет RUB. 392,97 RUB. Magnit. Доступно 159379,43 RUB
    private static final Pattern TBANK_P = Pattern.compile(
            "(Пополнение|Зачисление|Поступление|Возврат|Перевод|Покупка|Оплата|Списание|Снятие|Платеж|Платёж)"
                    + "[^0-9₽]*?" + AMT + "\\s*" + CUR + "(.*?)(?:Доступно|Баланс|$)");
    private static final Pattern ANY_CARD = Pattern.compile("[*•]{1,2}\\s*(\\d{4})");

    private static final Pattern NOT_TX = Pattern.compile(
            "(?iu)никому не (?:говорите|сообщайте)|\\bкод\\b|\\bkod\\b|отказ|отклонен|не удалось|недостаточно средств");

    private RuBankRules() {
    }

    public static boolean isOurPackage(String pkg) {
        return TBANK.equals(pkg) || SBER.equals(pkg) || RAIF.equals(pkg);
    }

    /**
     * @param pkg source app package; if null/empty all rules are tried (e.g. for SMS)
     * @return parsed transaction or null if the text is not a recognised money movement
     */
    public static Match match(String rawText, String pkg) {
        if (rawText == null) {
            return null;
        }
        String text = normalize(rawText);
        if (text.isEmpty() || NOT_TX.matcher(text).find()) {
            return null;
        }
        boolean any = pkg == null || pkg.isEmpty();
        if (any || SBER.equals(pkg)) {
            Match m = sber(text);
            if (m != null || !any) {
                return m;
            }
        }
        if (any || RAIF.equals(pkg)) {
            Match m = raif(text);
            if (m != null || !any) {
                return m;
            }
        }
        if (any || TBANK.equals(pkg)) {
            return tbank(text);
        }
        return null;
    }

    private static Match sber(String text) {
        Matcher m = SBER_P.matcher(text);
        if (!m.find()) {
            return null;
        }
        Matcher c = SBER_CARD.matcher(text);
        return new Match(SBER, "➕".equals(m.group(1)), amount(m.group(2), m.group(3)),
                cleanPayee(m.group(4)), c.find() ? c.group(1) : "");
    }

    private static Match raif(String text) {
        Matcher m = RAIF_P.matcher(text);
        if (!m.find()) {
            return null;
        }
        return new Match(RAIF, "+".equals(m.group(2)), amount(m.group(3), m.group(4)),
                cleanPayee(m.group(5)), m.group(1));
    }

    private static Match tbank(String text) {
        Matcher m = TBANK_P.matcher(text);
        if (!m.find()) {
            return null;
        }
        String kind = m.group(1);
        boolean income = kind.equals("Пополнение") || kind.equals("Зачисление")
                || kind.equals("Поступление") || kind.equals("Возврат");
        String payee = cleanPayee(m.group(4));
        if (payee.isEmpty()) {
            // Push puts the counterparty bank in the title, right before the operation kind.
            payee = cleanPayee(text.substring(0, m.start()));
        }
        Matcher c = ANY_CARD.matcher(text);
        return new Match(TBANK, income, amount(m.group(2), m.group(3)), payee, c.find() ? c.group(1) : "");
    }

    static String normalize(String s) {
        return s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
                .replace('−', '-').replace('–', '-')
                .replaceAll("^\\s*null\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String amount(String whole, String frac) {
        String w = whole.replace(" ", "");
        if (frac == null || frac.isEmpty()) {
            return w + ".00";
        }
        return w + "." + (frac.length() == 1 ? frac + "0" : frac);
    }

    static String cleanPayee(String s) {
        if (s == null) {
            return "";
        }
        String p = s
                .replaceAll("(?iu)^[\\s,.]*сч[её]т\\s+RUB[.,]?", "")
                .replaceAll("(?iu)по номеру телефона", "")
                .replaceAll("(?iu)через СБП|по СБП", "")
                .replaceAll("\\+?\\d[\\d ()-]{9,}\\d", "")
                .replaceAll("(?iu)^[\\s,.]*(?:в|от|на)\\s+", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\s,.:-]+|[\\s,:-]+$", "");
        // Drop a trailing sentence dot but keep initials like "Максим Ц."
        if (p.endsWith(".") && !p.matches(".*\\s\\p{Lu}\\.$")) {
            p = p.substring(0, p.length() - 1).trim();
        }
        return p;
    }
}
