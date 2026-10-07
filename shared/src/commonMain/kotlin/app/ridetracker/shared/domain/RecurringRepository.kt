package app.ridetracker.shared.domain

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.ridetracker.shared.data.AppDatabase
import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.RecurringExpenseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** A recurring expense whose next occurrence is due and waiting for the driver's answer. */
data class PendingExpense(val rule: RecurringExpenseEntity, val dueDate: LocalDate)

class RecurringRepository(private val database: AppDatabase) {
    private val dao = database.recurringExpenseDao()

    fun observeAll(): Flow<List<RecurringExpenseEntity>> = dao.observeAll()

    /** Due occurrences, oldest first: one per recurring expense (the next is asked after this one). */
    fun observePending(today: LocalDate): Flow<List<PendingExpense>> = dao.observeAll().map { pendingOf(it, today) }

    suspend fun getPending(today: LocalDate): List<PendingExpense> = pendingOf(dao.getAll(), today)

    suspend fun get(id: Long): RecurringExpenseEntity? = dao.getById(id)

    /** Starts a series from an expense the driver just added on [firstDate]. */
    suspend fun startFrom(
        firstDate: LocalDate,
        amountMinor: Long,
        category: ExpenseCategory,
        note: String?,
        frequency: Frequency,
        endDate: LocalDate?,
        nowEpochMillis: Long,
    ): Long = dao.insert(
        RecurringExpenseEntity(
            amountMinor = amountMinor,
            category = category.id,
            note = note?.trim()?.ifEmpty { null },
            frequency = frequency.id,
            anchorDate = firstDate.toEpochDays(),
            nextDueDate = Recurrence.nextAfter(firstDate, firstDate, frequency).toEpochDays(),
            endDate = endDate?.toEpochDays(),
            createdAt = nowEpochMillis,
        ),
    )

    suspend fun update(rule: RecurringExpenseEntity) = dao.update(rule)

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Adds the due occurrence as an expense and moves on to the next one, in one transaction. */
    suspend fun accept(ruleId: Long, dueDate: LocalDate, nowEpochMillis: Long) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val rule = dao.getById(ruleId) ?: return@immediateTransaction
                // Ignore stale requests (e.g. a notification tapped after the app already handled it).
                if (rule.nextDueDate != dueDate.toEpochDays()) return@immediateTransaction
                database.expenseDao().insert(
                    ExpenseEntity(
                        amountMinor = rule.amountMinor,
                        date = rule.nextDueDate,
                        category = rule.category,
                        note = rule.note,
                        createdAt = nowEpochMillis,
                    ),
                )
                dao.update(advance(rule))
            }
        }
    }

    /** Skips the due occurrence without adding an expense. */
    suspend fun skip(ruleId: Long, dueDate: LocalDate) {
        val rule = dao.getById(ruleId) ?: return
        if (rule.nextDueDate != dueDate.toEpochDays()) return
        dao.update(advance(rule))
    }

    suspend fun markNotified(rule: RecurringExpenseEntity) {
        dao.update(rule.copy(notifiedDueDate = rule.nextDueDate))
    }

    private fun advance(rule: RecurringExpenseEntity): RecurringExpenseEntity {
        val next = Recurrence.nextAfter(
            LocalDate.fromEpochDays(rule.nextDueDate),
            LocalDate.fromEpochDays(rule.anchorDate),
            Frequency.fromId(rule.frequency),
        )
        return rule.copy(nextDueDate = next.toEpochDays())
    }

    private fun pendingOf(rules: List<RecurringExpenseEntity>, today: LocalDate): List<PendingExpense> =
        rules.mapNotNull { rule ->
            val due = LocalDate.fromEpochDays(rule.nextDueDate)
            val end = rule.endDate?.let { LocalDate.fromEpochDays(it) }
            if (Recurrence.isPending(due, end, today)) PendingExpense(rule, due) else null
        }.sortedBy { it.dueDate }
}
