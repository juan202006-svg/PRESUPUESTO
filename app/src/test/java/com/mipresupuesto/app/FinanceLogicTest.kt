package com.mipresupuesto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FinanceLogicTest {
    @Test fun parseMoneyInColombianPesos() {
        assertEquals(1250000L, parseCop("$1.250.000"))
        assertEquals(50000L, parseCop("50 000"))
        assertNull(parseCop(""))
        assertNull(parseCop("0"))
        assertNull(parseCop("-200"))
        assertNull(parseCop("1,5"))
    }

    @Test fun balanceAndBudgetAreSeparate() {
        val day = LocalDate.of(2026, 10, 5).toEpochDay()
        val data = listOf(
            Movement(id = 1, kind = "INGRESO", amount = 2500000L, category = "sueldo", day = day),
            Movement(id = 2, kind = "GASTO", amount = 350000L, category = "alimentacion", day = day),
            Movement(id = 3, kind = "GASTO", amount = 150000L, category = "transporte", day = day)
        )
        val stats = summarize(data, listOf(Budget("2026-10", "TOTAL", 900000L)))
        assertEquals(2500000L, stats.income)
        assertEquals(500000L, stats.expense)
        assertEquals(2000000L, stats.balance)
        assertEquals(400000L, stats.remainingBudget)
        assertEquals("2026-10", movementMonth(day))
    }

    @Test fun csvEscapesInjectedSpreadsheetFormulas() {
        val day = LocalDate.of(2026, 10, 1).toEpochDay()
        val csv = buildCsv(listOf(Movement(
            id = 1, kind = "GASTO", amount = 100L, category = "otros", day = day,
            note = "=HYPERLINK(\"https://example.com\")"
        )))
        assert(csv.contains("\"'=HYPERLINK(\"\"https://example.com\"\")\""))
    }
}
