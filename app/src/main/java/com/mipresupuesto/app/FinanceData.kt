package com.mipresupuesto.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "movements", indices = [Index("day"), Index("kind")])
data class Movement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val amount: Long,
    val category: String,
    val day: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "budgets", primaryKeys = ["month", "category"])
data class Budget(
    val month: String,
    val category: String, // TOTAL para límite mensual general
    val amount: Long
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM movements ORDER BY day DESC, id DESC")
    fun observeMovements(): Flow<List<Movement>>

    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM movements ORDER BY day DESC, id DESC")
    suspend fun allMovements(): List<Movement>

    @Query("SELECT * FROM budgets ORDER BY month DESC, category")
    suspend fun allBudgets(): List<Budget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMovement(value: Movement)

    @Delete
    suspend fun deleteMovement(value: Movement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(value: Budget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMovements(values: List<Movement>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudgets(values: List<Budget>)

    @Query("DELETE FROM movements")
    suspend fun clearMovements()

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

    @Query("DELETE FROM budgets WHERE month = :month AND category = :category")
    suspend fun deleteBudget(month: String, category: String)
}

@Database(entities = [Movement::class, Budget::class], version = 1, exportSchema = false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        @Volatile private var instance: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                "mi_presupuesto.db"
            ).build().also { instance = it }
        }
    }
}
