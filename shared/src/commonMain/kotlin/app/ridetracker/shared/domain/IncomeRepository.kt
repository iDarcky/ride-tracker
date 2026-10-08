package app.ridetracker.shared.domain

import app.ridetracker.shared.data.AppDatabase
import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.IncomeLineEntity
import app.ridetracker.shared.data.LineInRange
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.PlatformTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** An income entry and its breakdown lines, kept for Undo. */
data class DeletedEntry(val entry: IncomeEntryEntity, val lines: List<IncomeLineEntity>)

class IncomeRepository(database: AppDatabase) {
    private val platforms = database.platformDao()
    private val entries = database.incomeEntryDao()
    private val imports = database.importDao()

    fun observePlatforms(): Flow<List<PlatformEntity>> = platforms.observeAll()

    suspend fun addPlatform(name: String, colorArgb: Long): Long =
        platforms.insert(PlatformEntity(name = name.trim(), colorArgb = colorArgb, sortOrder = platforms.nextSortOrder()))

    suspend fun updatePlatform(platform: PlatformEntity) = platforms.update(platform.copy(name = platform.name.trim()))

    suspend fun getEntries(range: DateRange): List<EntryWithPlatform> =
        entries.getInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    fun observeAllEntries(): Flow<List<EntryWithPlatform>> = entries.observeAll()

    fun observeEntries(range: DateRange): Flow<List<EntryWithPlatform>> =
        entries.observeInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    fun observeTotals(range: DateRange): Flow<List<PlatformTotal>> =
        entries.observeTotalsInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    /** Entries with all their details (online time, cash…), for Home's statistics. */
    fun observeEntryDetails(range: DateRange): Flow<List<IncomeEntryEntity>> =
        entries.observeDetailsInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    fun observeLines(range: DateRange): Flow<List<LineInRange>> =
        entries.observeLinesInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    /** The earliest day with any income, expense or trip; null without data. */
    suspend fun firstDay(): LocalDate? = entries.firstDay()?.let(LocalDate::fromEpochDays)

    suspend fun getEntry(id: Long): IncomeEntryEntity? = entries.getById(id)

    suspend fun saveEntry(
        id: Long?,
        platformId: Long,
        amountMinor: Long,
        date: LocalDate,
        note: String?,
        nowEpochMillis: Long,
    ) {
        val cleanNote = note?.trim()?.ifEmpty { null }
        val existing = id?.let { entries.getById(it) }
        if (existing == null) {
            entries.insert(
                IncomeEntryEntity(
                    platformId = platformId,
                    amountMinor = amountMinor,
                    date = date.toEpochDays(),
                    note = cleanNote,
                    createdAt = nowEpochMillis,
                ),
            )
        } else {
            // Editing an estimated day makes it the driver's own: it is never recalculated after that.
            val source = if (existing.source == IncomeSource.ESTIMATE.id) IncomeSource.MANUAL.id else existing.source
            entries.update(
                existing.copy(platformId = platformId, amountMinor = amountMinor, date = date.toEpochDays(), note = cleanNote, source = source),
            )
        }
    }

    suspend fun deleteEntry(id: Long) = entries.deleteById(id)

    /** Deletes an entry and returns it with its breakdown, so Undo can bring both back. */
    suspend fun deleteForUndo(id: Long): DeletedEntry? {
        val entry = entries.getById(id) ?: return null
        val deleted = DeletedEntry(entry, imports.getLines(id))
        entries.deleteById(id) // lines go with it (cascade)
        return deleted
    }

    suspend fun restore(deleted: DeletedEntry) {
        entries.insert(deleted.entry)
        imports.insertLines(deleted.lines)
    }
}
