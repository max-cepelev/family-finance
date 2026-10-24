import com.realbyte.money.rumod.RuBankRules;
import com.realbyte.money.rumod.RuBankRules.Match;

/** Run: java RuBankRulesTest (see build.sh). Exits non-zero on failure. */
public class RuBankRulesTest {
    private static int failures = 0;

    private static void expect(String pkg, String text, String want) {
        Match m = RuBankRules.match(text, pkg);
        String got = m == null ? "null" : (m.income ? "+" : "-") + m.amount + " [" + m.payee + "] " + m.card;
        boolean ok = got.equals(want);
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "ok   " : "FAIL ") + got + (ok ? "" : "   want: " + want) + "   <- " + text.replace("\n", "⏎"));
    }

    // NotificationListener prefixes the ticker, which is "null" when the app sets none.
    private static String push(String title, String text) {
        return "null " + title + " " + text;
    }

    public static void main(String[] args) {
        String S = RuBankRules.SBER, R = RuBankRules.RAIF, T = RuBankRules.TBANK;

        // Sber push: amount in the title, balance in the body
        expect(S, push("➕ 10 ₽ по СБП от МАКСИМ АЛЕКСАНДРОВИЧ Ц.", "💳 10 ₽ •• 2824"), "+10.00 [МАКСИМ АЛЕКСАНДРОВИЧ Ц.] 2824");
        expect(S, push("➖ 10 ₽ Райффайзенбанк", "💳 0 ₽ •• 2824"), "-10.00 [Райффайзенбанк] 2824");
        expect(S, push("➖ 1 349,50 ₽ O'STIN", "💳 12 000 ₽ •• 2824"), "-1349.50 [O'STIN] 2824");

        // Raiffeisen push
        expect(R, push("Заплатили картой  *5790", "- 17.00 ₽ в MARMALATO 086. Теперь на карте 14 025.10 ₽"), "-17.00 [MARMALATO 086] 5790");
        expect(R, push("Заплатили картой  *5790", "- 349.00 ₽ в O`Stin. Теперь на карте 14 042.10 ₽"), "-349.00 [O`Stin] 5790");
        expect(R, push("Перевели со счета *5474", "-10.00 ₽ по номеру телефона +79194990663, Максим Александрович Ц. через СБП, теперь на счете 14 015.10 ₽"), "-10.00 [Максим Александрович Ц.] 5474");
        expect(R, push("Пришел перевод на счет *5474", "+ 10.00 ₽ от +79194990663, Максим Александрович Ц. Теперь на счете 14 025.10 ₽"), "+10.00 [Максим Александрович Ц.] 5474");
        expect(R, push("Raiffeisen", "У вас 2 уведомления от Raiffeisen"), "null");

        // T-Bank push: counterparty bank in the title, two-line body
        expect(T, push("Райффайзенбанк", "Пополнение на 10 ₽, счет RUB. Максим Ц.\nДоступно 10 ₽"), "+10.00 [Максим Ц.] ");
        expect(T, push("Сбербанк", "Перевод на 10 ₽, счет RUB. Максим Ц.\nБаланс 0 ₽"), "-10.00 [Максим Ц.] ");
        expect(T, push("Райффайзенбанк", "Пополнение на 10 ₽, счет RUB.\nДоступно 10 ₽"), "+10.00 [Райффайзенбанк] ");

        // Old T-Bank / Tinkoff SMS (no package)
        expect(null, "Пополнение, счет RUB. 250 RUB. Семен Д. Доступно 10711 RUB", "+250.00 [Семен Д.] ");
        expect(null, "Оплата СБП, счет RUB. 392,97 RUB. Magnit. Доступно 159379,43 RUB", "-392.97 [Magnit] ");
        expect(null, "Пополнение. Счет RUB. 9995.1 RUB. Др. банк. Доступно 9995.1 RUB", "+9995.10 [Др. банк] ");

        // Not transactions
        expect(null, "Никому не говорите код 2286! Перевод юридическому лицу 11052.00 RUB", "null");
        expect(null, "Отказ REG.RU. Недостаточно средств. Карта *9048", "null");
        expect(null, "Пришлем 5000 р. Посоветуйте бесплатную кредитку: t.tb.ru/p7dG7c", "null");
        expect(T, push("Т-Банк", "Вход в личный кабинет Т-Банк в 12:14 29.09.26"), "null");

        // Package mismatch: a Sber-looking text from the T-Bank app must not use the Sber rule
        expect(T, push("➖ 10 ₽ Райффайзенбанк", "💳 0 ₽ •• 2824"), "null");

        System.out.println(failures == 0 ? "\nALL PASSED" : "\n" + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
