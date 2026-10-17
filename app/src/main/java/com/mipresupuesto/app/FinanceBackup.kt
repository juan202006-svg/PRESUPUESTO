package com.mipresupuesto.app

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * Copia de seguridad offline en JSON, independiente de la base de datos interna.
 * No se realizan conexiones de red ni se piden permisos de almacenamiento.
 * El usuario elige un documento mediante Storage Access Framework.
 */
private const val FORMAT_VERSION = 1
private const val MAX_MOVEMENTS = 100_000
private const val MAX_BUDGETS = 20_000

fun encodeBackup(movements: List<Movement>, budgets: List<Budget>): String =
    JSONObject().apply {
        put("application", "Mi Presupuesto")
        put("version", FORMAT_VERSION)
        put("movements", JSONArray().apply {
            movements.forEach { move ->
                put(JSONObject().apply {
                    put("id", move.id)
                    put("kind", move.kind)
                    put("amount", move.amount)
                    put("category", move.category)
                    put("day", move.day)
                    put("note", move.note)
                    put("createdAt", move.createdAt)
                })
            }
        })
        put("budgets", JSONArray().apply {
            budgets.forEach { budget ->
                put(JSONObject().apply {
                    put("month", budget.month)
                    put("category", budget.category)
                    put("amount", budget.amount)
                })
            }
        })
    }.toString(2)

data class ParsedBackup(val movements: List<Movement>, val budgets: List<Budget>)

fun decodeBackup(text: String): ParsedBackup {
    require(text.length <= 15_000_000) { "El archivo es demasiado grande." }
    val root = JSONObject(text)
    require(root.getString("application") == "Mi Presupuesto") { "No es una copia de Mi Presupuesto." }
    require(root.getInt("version") == FORMAT_VERSION) { "Versión de copia de seguridad no compatible." }
    val movesJson = root.getJSONArray("movements")
    val budgetsJson = root.getJSONArray("budgets")
    require(movesJson.length() <= MAX_MOVEMENTS && budgetsJson.length() <= MAX_BUDGETS) {
        "La copia contiene demasiados registros."
    }
    val movements = (0 until movesJson.length()).map { idx ->
        val item = movesJson.getJSONObject(idx)
        val kind = item.getString("kind")
        val category = item.getString("category")
        val amount = item.getLong("amount")
        val day = item.getLong("day")
        val note = item.getString("note")
        val id = item.getLong("id")
        val createdAt = item.getLong("createdAt")
        require(kind == "INGRESO" || kind == "GASTO") { "Tipo de movimiento inválido." }
        val validCategories = if (kind == "GASTO") expenseCategories else incomeCategories
        require(validCategories.any { it.key == category }) { "Categoría de movimiento inválida." }
        require(amount in 1..MAX_AMOUNT_COP && id > 0 && createdAt > 0 && note.length <= 180) {
            "Un movimiento contiene datos inválidos."
        }
        require(runCatching { LocalDate.ofEpochDay(day) }.isSuccess) { "Fecha inválida." }
        Movement(id, kind, amount, category, day, note, createdAt)
    }
    require(movements.map { it.id }.distinct().size == movements.size) { "Hay movimientos duplicados." }
    val budgets = (0 until budgetsJson.length()).map { idx ->
        val item = budgetsJson.getJSONObject(idx)
        val month = item.getString("month")
        val category = item.getString("category")
        val amount = item.getLong("amount")
        require(runCatching { YearMonth.parse(month) }.isSuccess) { "Mes de presupuesto inválido." }
        require(category == "TOTAL" || expenseCategories.any { it.key == category }) {
            "Categoría de presupuesto inválida."
        }
        require(amount in 1..MAX_AMOUNT_COP) { "Límite de presupuesto inválido." }
        Budget(month, category, amount)
    }
    require(budgets.map { it.month to it.category }.distinct().size == budgets.size) {
        "Hay límites de presupuesto duplicados."
    }
    return ParsedBackup(movements, budgets)
}

/** Los datos actuales solo se borran después de validar toda la copia. */
suspend fun FinanceDatabase.restore(backup: ParsedBackup) {
    withTransaction {
        dao().clearMovements()
        dao().clearBudgets()
        dao().saveMovements(backup.movements)
        dao().saveBudgets(backup.budgets)
    }
}
