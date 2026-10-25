package com.familyfinance.domain.bank

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cases from real notifications (docs/bank-formats.md), names and phone numbers masked. */
class BankParserTest {

    private fun check(bank: Bank, title: String, text: String, want: String) {
        val e = BankParser.parse(bank, title, text)
        val got = e?.let { "${if (it.income) "+" else "-"}${it.amount} [${it.payee}] ${it.card ?: ""}".trim() } ?: "null"
        assertEquals("$title | $text", want, got)
    }

    @Test fun `sber push - amount in the title, card in the body`() {
        check(Bank.SBER, "➕ 10 ₽ по СБП от ИВАН ПЕТРОВИЧ С.", "💳 10 ₽ •• 2824", "+1000 [ИВАН ПЕТРОВИЧ С.] 2824")
        check(Bank.SBER, "➖ 10 ₽ Райффайзенбанк", "💳 0 ₽ •• 2824", "-1000 [Райффайзенбанк] 2824")
        check(Bank.SBER, "➖ 1 349,50 ₽ O'STIN", "💳 12 000 ₽ •• 2824", "-134950 [O'STIN] 2824")
    }

    @Test fun `raiffeisen push - sign before the amount, card or account in the title`() {
        check(Bank.RAIF, "Заплатили картой  *5790", "- 17.00 ₽ в MARMALATO 086. Теперь на карте 14 025.10 ₽", "-1700 [MARMALATO 086] 5790")
        check(Bank.RAIF, "Заплатили картой  *5790", "- 349.00 ₽ в O`Stin. Теперь на карте 14 042.10 ₽", "-34900 [O`Stin] 5790")
        check(Bank.RAIF, "Перевели со счета *5474", "-10.00 ₽ по номеру телефона +79990000000, Иван Петрович С. через СБП, теперь на счете 14 015.10 ₽", "-1000 [Иван Петрович С.] 5474")
        check(Bank.RAIF, "Пришел перевод на счет *5474", "+ 10.00 ₽ от +79990000000, Иван Петрович С. Теперь на счете 14 025.10 ₽", "+1000 [Иван Петрович С.] 5474")
        check(Bank.RAIF, "Raiffeisen", "У вас 2 уведомления от Raiffeisen", "null")
    }

    @Test fun `t-bank push - counterparty bank in the title, no card`() {
        check(Bank.TBANK, "Райффайзенбанк", "Пополнение на 10 ₽, счет RUB. Иван С.\nДоступно 10 ₽", "+1000 [Иван С.]")
        check(Bank.TBANK, "Сбербанк", "Перевод на 10 ₽, счет RUB. Иван С.\nБаланс 0 ₽", "-1000 [Иван С.]")
        check(Bank.TBANK, "Райффайзенбанк", "Пополнение на 10 ₽, счет RUB.\nДоступно 10 ₽", "+1000 [Райффайзенбанк]")
        check(Bank.TBANK, "Т-Банк", "Оплата СБП, счет RUB. 392,97 RUB. Magnit. Доступно 159379,43 RUB", "-39297 [Magnit]")
    }

    @Test fun `not money movements are skipped`() {
        check(Bank.TBANK, "Т-Банк", "Никому не говорите код 2286! Перевод юридическому лицу 11052.00 RUB", "null")
        check(Bank.TBANK, "Т-Банк", "Отказ REG.RU. Недостаточно средств. Карта *9048", "null")
        check(Bank.TBANK, "Т-Банк", "Вход в личный кабинет Т-Банк в 12:14 29.09.26", "null")
    }

    @Test fun `each bank uses only its own rule`() {
        // A Sber-looking text from the T-Bank app must not be read with the Sber rule.
        check(Bank.TBANK, "➖ 10 ₽ Райффайзенбанк", "💳 0 ₽ •• 2824", "null")
    }

    @Test fun `banks are found by package`() {
        assertEquals(Bank.SBER, Bank.byPackage("ru.sberbankmobile"))
        assertEquals(null, Bank.byPackage("com.example"))
    }
}
