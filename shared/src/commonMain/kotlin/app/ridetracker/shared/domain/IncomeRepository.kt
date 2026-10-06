package app.ridetracker.shared.domain

import app.ridetracker.shared.data.AppDatabase
import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.PlatformTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

class IncomeRepository(database: AppDatabase) {
    private val platforms = database.platformDao()
    private val entries = database.incomeEntryDao()

    fun observePlatforms(): Flow<List<PlatformEntity>> = platforms.observeAll()

    suspend fun addPlatform(name: String, colorArgb: Long): Long =
        platforms.insert(PlatformEntity(name = name.trim(), colorArgb = colorArgb, sortOrder = platforms.nextSortOrder()))

    suspend fun updatePlatform(platform: PlatformEntity) = platforms.update(platform.copy(name = platform.name.trim()))

    fun observeEntries(range: DateRange): Flow<List<EntryWithPlatform>> =
        entries.observeInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    fun observeTotals(range: DateRange): Flow<List<PlatformTotal>> =
        entries.observeTotalsInRange(range.start.toEpochDays(), range.endInclusive.toEpochDays())

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
            entries.update(
                existing.copy(platformId = platformId, amountMinor = amountMinor, date = date.toEpochDays(), note = cleanNote),
            )
        }
    }

    suspend fun deleteEntry(id: Long) = entries.deleteById(id)

    /** Re-inserts a deleted entry with its original id (used for Undo). */
    suspend fun restoreEntry(entry: IncomeEntryEntity) {
        entries.insert(entry)
    }
}
