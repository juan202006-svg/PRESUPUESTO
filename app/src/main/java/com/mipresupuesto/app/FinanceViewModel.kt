package com.mipresupuesto.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth


data class FinanceState(
    val month: String = currentMonth(),
    val movements: List<Movement> = emptyList(),
    val allMovements: List<Movement> = emptyList(),
    val budgets: List<Budget> = emptyList()
) {
    val stats: MonthStats get() = summarize(movements, budgets)
    fun limitFor(category: String): Long? = budgets.firstOrNull { it.category == category }?.amount
    fun spentOn(category: String): Long = movements
        .filter { it.kind == "GASTO" && it.category == category }.sumOf { it.amount }
}

class FinanceViewModel(application: Application): AndroidViewModel(application) {
    private val db = FinanceDatabase.get(application)
    private val dao = db.dao()
    private val selectedMonth = MutableStateFlow(currentMonth())

    val state = combine(dao.observeMovements(), dao.observeBudgets(), selectedMonth) { moves, limits, month ->
        FinanceState(
            month = month,
            movements = moves.filter { movementMonth(it.day) == month },
            allMovements = moves,
            budgets = limits.filter { it.month == month }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceState())

    fun changeMonth(delta: Long) {
        selectedMonth.value = YearMonth.parse(selectedMonth.value).plusMonths(delta).toString()
    }

    fun saveMovement(movement: Movement) = viewModelScope.launch { dao.saveMovement(movement) }
    fun deleteMovement(movement: Movement) = viewModelScope.launch { dao.deleteMovement(movement) }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        buildCsv(dao.allMovements())
    }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        encodeBackup(dao.allMovements(), dao.allBudgets())
    }

    suspend fun importBackup(text: String) = withContext(Dispatchers.IO) {
        // parseo completo antes de una única transacción atómica
        db.restore(decodeBackup(text))
    }

    fun saveLimit(month: String, category: String, amount: Long?) = viewModelScope.launch {
        if (amount == null || amount <= 0) dao.deleteBudget(month, category)
        else dao.saveBudget(Budget(month, category, amount))
    }
}
