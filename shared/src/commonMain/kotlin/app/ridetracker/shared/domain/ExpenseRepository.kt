package app.ridetracker.shared.domain

import app.ridetracker.shared.data.AppDatabase
import app.ridetracker.shared.data.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

class ExpenseRepository(database: AppDatabase) {
    private val expenses = database.expenseDao()

    fun observeInRange(range: DateRange): Flow<List<ExpenseEntity>> =
        expenses.observeInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    suspend fun getInRange(range: DateRange): List<ExpenseEntity> =
        expenses.getInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    fun observeAll(): Flow<List<ExpenseEntity>> = expenses.observeAll()

    /** True once the user has logged any expense; Home then shows "money kept". */
    fun observeAny(): Flow<Boolean> = expenses.observeAny()

    suspend fun get(id: Long): ExpenseEntity? = expenses.getById(id)

    suspend fun save(
        id: Long?,
        amountMinor: Long,
        date: LocalDate,
        category: ExpenseCategory,
        note: String?,
        nowEpochMillis: Long,
    ) {
        val cleanNote = note?.trim()?.ifEmpty { null }
        val existing = id?.let { expenses.getById(it) }
        if (existing == null) {
            expenses.insert(
                ExpenseEntity(
                    amountMinor = amountMinor,
                    date = date.toEpochDays(),
                    category = category.id,
                    note = cleanNote,
                    createdAt = nowEpochMillis,
                ),
            )
        } else {
            expenses.update(existing.copy(amountMinor = amountMinor, date = date.toEpochDays(), category = category.id, note = cleanNote))
        }
    }

    suspend fun delete(id: Long) = expenses.deleteById(id)

    /** Re-inserts a deleted expense with its original id (used for Undo). */
    suspend fun restore(expense: ExpenseEntity) {
        expenses.insert(expense)
    }
}
