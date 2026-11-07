package com.familyfinance.domain.csv

import com.familyfinance.domain.bank.Bank
import com.familyfinance.domain.model.TransactionType
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// Statement lines in the Raiffeisen layout; payees and numbers made up.
class CsvTest {
    private val raifHeader = "﻿Дата операции;Выполнено банком;Номер документа;Сумма в валюте операции (поступления);" +
        "Сумма в валюте операции (расходы);Валюта операции;Сумма в валюте счета (поступления);Сумма в валюте счета (расходы);" +
        "Валюта счета;Детали операции (назначение платежа);Номер карты"

    @Test fun `quotes, doubled quotes and line ends`() {
        val rows = Csv.parse("a;\"b;c\";\"say \"\"hi\"\"\"\r\n1;2;3\n\n")
        assertEquals(listOf(listOf("a", "b;c", "say \"hi\""), listOf("1", "2", "3")), rows)
        assertEquals("a;\"b;c\";\"say \"\"hi\"\"\"", Csv.line(listOf("a", "b;c", "say \"hi\"")))
    }

    @Test fun `amounts and dates as banks write them`() {
        assertEquals(133078L, Csv.amount("1 330,78"))
        assertEquals(5240000L, Csv.amount("52 400"))
        assertEquals(-1050L, Csv.amount("-10.5"))
        assertNull(Csv.amount("abc"))
        assertEquals(LocalDateTime.of(2026, 10, 26, 7, 48), Csv.dateTime("26.10.2026 07:48"))
        assertEquals(LocalDateTime.of(2026, 10, 10, 0, 0), Csv.dateTime("10.10.2026"))
        assertNull(Csv.dateTime("2026-10-10"))
    }

    @Test fun `raiffeisen statement`() {
        val text = raifHeader + "\r\n" +
            "\"26.10.2026 07:48\";\"В обработке\";\"ZR01\";\"\";\"1 330,78\";\"RUB\";\"\";\"1 330,78\";\"RUB\";\" Пятерочка\";\"*1234\"\r\n" +
            "\"10.10.2026 12:00\";\"10.10.2026\";\"\";\"52 400,00\";\"\";\"RUB\";\"52 400,00\";\"\";\"RUB\";\"рублевый перевод,Заработная плата\";\"\"\r\n" +
            "\"bad\";\"\";\"ZR03\";\"\";\"1\";\"RUB\";\"\";\"1\";\"RUB\";\"x\";\"\"\r\n"
        val statement = BankStatements.parse(Csv.parse(text))!!
        assertEquals(Bank.RAIF, statement.bank)
        assertEquals(listOf(4), statement.skipped)
        val (shop, salary) = statement.rows
        assertEquals(TransactionType.EXPENSE, shop.type)
        assertEquals(133078L, shop.amount)
        assertEquals("Пятерочка", shop.payee)
        assertEquals("1234", shop.card)
        assertEquals("raif:ZR01", statement.externalId(shop))
        assertEquals(TransactionType.INCOME, salary.type)
        assertEquals("Заработная плата", salary.payee)
        assertNull(salary.card)
        // No document number: a stand-in made of what the operation is.
        assertTrue(salary.reference.startsWith("auto-2026-10-10T12:00-INCOME-5240000-"))
        assertNull(BankStatements.parse(Csv.parse("дата;тип\n1;2")))
    }

    @Test fun `app format round trip`() {
        val ops = listOf(
            CsvOperation(LocalDateTime.of(2026, 10, 26, 7, 48), TransactionType.EXPENSE, 133078, "RUB", "Карта", category = "Питание", subcategory = "Продукты", note = "Пятерочка; у дома"),
            CsvOperation(LocalDateTime.of(2026, 10, 27, 9, 0), TransactionType.TRANSFER, 500000, "RUB", "Сбербанк", toAccount = "Наличные"),
        )
        val rows = Csv.parse(AppCsv.write(ops))
        assertTrue(AppCsv.isAppFormat(rows))
        val (back, errors) = AppCsv.parse(rows)
        assertEquals(emptyList<CsvLineError>(), errors)
        assertEquals(ops.mapIndexed { i, op -> op.copy(line = i + 2) }, back)
    }

    @Test fun `bad lines are reported, the rest is read`() {
        val text = "дата;тип;сумма;валюта;счёт;на счёт;категория;подкатегория;заметка\n" +
            "01.08.2026;расход;-350;;Наличные;;Кафе;;\n" +
            "02.08.2026;покупка;10;RUB;Наличные;;;;\n" +
            "03.08.2026;перевод;10;RUB;Наличные;;;;\n"
        val (ops, errors) = AppCsv.parse(Csv.parse(text))
        assertEquals(1, ops.size)
        assertEquals(35000L, ops[0].amount)
        assertEquals("RUB", ops[0].currency)
        assertEquals(listOf(3, 4), errors.map { it.line })
    }

    @Test fun `exported refunds preserve negative amounts without changing legacy spreadsheet signs`() {
        val ops = TransactionType.entries.map { type ->
            CsvOperation(LocalDateTime.of(2026, 10, 26, 12, 0), type, -1250, "RUB", "Карта",
                toAccount = "Наличные".takeIf { type == TransactionType.TRANSFER }, note = "Возврат")
        }
        val (back, errors) = AppCsv.parse(Csv.parse(AppCsv.write(ops)))
        assertTrue(errors.isEmpty())
        assertEquals(ops.mapIndexed { i, op -> op.copy(line = i + 2) }, back)
        // Legacy manually written expenses with a minus still mean ordinary expenses.
        val legacy = listOf(AppCsv.HEADER, listOf("26.10.2026", "расход", "-12,50", "RUB", "Карта", "", "", "", ""))
        assertEquals(1250L, AppCsv.parse(legacy).first.single().amount)
    }

    @Test fun `names match exactly or by a common start`() {
        val accounts = listOf("Сбербанк", "Наличные", "Карта Райффайзен")
        assertEquals(NameMatch.Match("Наличные", true), NameMatch.find("наличные", accounts) { it })
        assertEquals(NameMatch.Match("Сбербанк", false), NameMatch.find("Сбер", accounts) { it })
        assertNull(NameMatch.find("Карта Лены", accounts) { it })
    }

    @Test fun `real statement when given`() {
        val path = System.getProperty("statement.path").orEmpty()
        assumeTrue("statement.path not set", path.isNotEmpty() && File(path).isFile)
        val statement = BankStatements.parse(Csv.parse(File(path).readText()))!!
        println("statement: ${statement.rows.size} rows, skipped ${statement.skipped}, income ${statement.rows.count { it.type == TransactionType.INCOME }}")
        assertTrue(statement.skipped.isEmpty())
    }
}
