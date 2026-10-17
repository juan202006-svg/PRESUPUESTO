package com.mipresupuesto.app

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val colombia = Locale.forLanguageTag("es-CO")
const val MAX_AMOUNT_COP = 999_999_999_999L

fun cop(amount: Long): String = "$" + NumberFormat.getIntegerInstance(colombia).format(amount)

fun parseCop(text: String): Long? {
    // Al ingresar dinero en COP trabajamos solo con pesos enteros.
    val normalized = text.trim().replace("$", "").replace(".", "").replace(" ", "")
    if (normalized.isEmpty() || !normalized.all { it.isDigit() }) return null
    return normalized.toLongOrNull()?.takeIf { it in 1..MAX_AMOUNT_COP }
}

fun currentMonth(): String = YearMonth.now().toString()
fun firstDayForSelectedMonth(month: String): Long =
    if (month == currentMonth()) LocalDate.now().toEpochDay()
    else YearMonth.parse(month).atDay(1).toEpochDay()
fun displayMonth(month: String): String = YearMonth.parse(month)
    .format(DateTimeFormatter.ofPattern("MMMM yyyy", colombia))
    .replaceFirstChar { it.titlecase(colombia) }

fun movementMonth(day: Long): String = YearMonth.from(LocalDate.ofEpochDay(day)).toString()
fun displayDay(day: Long): String = LocalDate.ofEpochDay(day)
    .format(DateTimeFormatter.ofPattern("dd MMM yyyy", colombia))

// El valor del presupuesto mensual no es dinero que se suma al saldo.
// El saldo del mes = ingresos - gastos; disponible presupuestado = límite - gastos.
data class MonthStats(val income: Long, val expense: Long, val balance: Long, val budget: Long?) {
    val remainingBudget: Long? get() = budget?.minus(expense)
    val budgetUsage: Float get() = if (budget == null || budget <= 0) 0f
        else (expense.toDouble() / budget.toDouble()).toFloat().coerceIn(0f, 1f)
}
fun summarize(movements: List<Movement>, budgets: List<Budget>): MonthStats {
    val income = movements.filter { it.kind == "INGRESO" }.sumOf { it.amount }
    val expense = movements.filter { it.kind == "GASTO" }.sumOf { it.amount }
    return MonthStats(income, expense, income - expense, budgets.find { it.category == "TOTAL" }?.amount)
}

data class Category(val key: String, val label: String, val emoji: String, val color: Long)

val expenseCategories = listOf(
    Category("alimentacion", "Alimentación", "🥑", 0xFF43A68C),
    Category("transporte", "Transporte", "🚌", 0xFF5282D3),
    Category("vivienda", "Vivienda", "🏡", 0xFFB585D5),
    Category("servicios", "Servicios", "💡", 0xFFE2A74B),
    Category("salud", "Salud", "💊", 0xFFDA7B7B),
    Category("educacion", "Educación", "📚", 0xFF53AFC1),
    Category("entretenimiento", "Diversión", "🎮", 0xFFE88A9C),
    Category("compras", "Compras", "🛍️", 0xFF9B92DD),
    Category("otros", "Otros", "📦", 0xFF82958F)
)
val incomeCategories = listOf(
    Category("sueldo", "Sueldo", "💼", 0xFF43A68C),
    Category("ventas", "Ventas", "🛒", 0xFF5282D3),
    Category("regalos", "Regalos", "🎁", 0xFFB585D5),
    Category("otro_ingreso", "Otros ingresos", "✨", 0xFFE2A74B)
)
fun categoryFor(movement: Movement): Category =
    (if (movement.kind == "INGRESO") incomeCategories else expenseCategories)
        .firstOrNull { it.key == movement.category }
        ?: Category(movement.category, movement.category, "💰", 0xFF82958F)

// CSV con comillas y protección de fórmulas al abrirlo en una hoja de cálculo.
private fun csvCell(value: String): String {
    val safe = if (Regex("^[=+@-]").containsMatchIn(value.trimStart())) "'" + value else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
}
fun buildCsv(movements: List<Movement>): String = buildString {
    append('\uFEFF')
    append("Fecha,Tipo,Categoria,Valor_COP,Nota\r\n")
    movements.forEach { movement ->
        val cells = listOf(
            LocalDate.ofEpochDay(movement.day).toString(),
            movement.kind,
            categoryFor(movement).label,
            movement.amount.toString(),
            movement.note
        )
        append(cells.joinToString(",") { csvCell(it) })
        append("\r\n")
    }
}
