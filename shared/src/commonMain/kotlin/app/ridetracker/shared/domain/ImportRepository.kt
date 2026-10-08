package app.ridetracker.shared.domain

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.ridetracker.shared.data.AppDatabase
import app.ridetracker.shared.data.ImportBatchEntity
import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.IncomeLineEntity
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.TripEntity
import app.ridetracker.shared.domain.importing.ParsedDay
import app.ridetracker.shared.domain.importing.ParsedSummary
import app.ridetracker.shared.domain.importing.ParsedTrip
import kotlinx.coroutines.flow.Flow

/** What saving an import did. */
sealed interface ImportOutcome {
    /** [saved] entries, trips or summaries were added; [skipped] were already in the app. */
    data class Saved(val batchId: Long, val saved: Int, val skipped: Int) : ImportOutcome

    /** This exact file was imported before. */
    data object AlreadyImported : ImportOutcome
}

/** Saves imported reports. Each import is one [ImportBatchEntity], so it shows in Import history. */
class ImportRepository(private val database: AppDatabase) {
    private val imports = database.importDao()
    private val entries = database.incomeEntryDao()

    fun observeBatches(): Flow<List<ImportBatchEntity>> = imports.observeBatches()

    suspend fun isImported(fileHash: String): Boolean = imports.hasFile(fileHash)

    suspend fun getLines(entryId: Long): List<IncomeLineEntity> = imports.getLines(entryId)

    /** How many entries already exist for [platformId] on the day of [day] (they are replaced on save). */
    suspend fun existingEntries(platformId: Long, day: ParsedDay): Int =
        entries.getForDay(platformId, day.date.toEpochDays()).size

    /**
     * Saves one day for one app. The report is the truth for that app and day: entries already there
     * (typed in or imported before) are replaced by one entry with the full breakdown.
     */
    suspend fun saveDay(
        platformId: Long,
        kind: ImportKind,
        source: IncomeSource,
        fileHash: String,
        day: ParsedDay,
        nowEpochMillis: Long,
    ): ImportOutcome = transaction {
        if (imports.hasFile(fileHash)) return@transaction ImportOutcome.AlreadyImported
        val epochDay = day.date.toEpochDays()
        val batchId = imports.insertBatch(
            ImportBatchEntity(
                platformId = platformId, kind = kind.id, fileHash = fileHash, periodStart = epochDay, periodEnd = epochDay,
                itemCount = 1, importedAt = nowEpochMillis,
            ),
        )
        val existing = entries.getForDay(platformId, epochDay)
        existing.forEach { entries.deleteById(it.id) } // lines go with them (cascade)
        val entryId = entries.insert(
            IncomeEntryEntity(
                platformId = platformId,
                amountMinor = day.earningsMinor,
                date = epochDay,
                note = existing.mapNotNull { it.note }.distinct().joinToString(" · ").ifEmpty { null },
                createdAt = existing.minOfOrNull { it.createdAt } ?: nowEpochMillis,
                source = source.id,
                importBatchId = batchId,
                cashCollectedMinor = day.cashCollectedMinor,
                onlineMinutes = existing.firstNotNullOfOrNull { it.onlineMinutes },
                tripCount = existing.firstNotNullOfOrNull { it.tripCount },
            ),
        )
        imports.insertLines(day.lines.map { IncomeLineEntity(entryId = entryId, kind = it.kind.id, amountMinor = it.amountMinor, inCash = it.inCash, label = it.label) })
        ImportOutcome.Saved(batchId, saved = 1, skipped = 0)
    }

    /** Adds trips not already in the app (matched by the platform's own id). */
    suspend fun saveTrips(
        platformId: Long,
        kind: ImportKind,
        fileHash: String,
        trips: List<ParsedTrip>,
        nowEpochMillis: Long,
    ): ImportOutcome = transaction {
        if (imports.hasFile(fileHash)) return@transaction ImportOutcome.AlreadyImported
        val known = trips.map { it.externalId }.chunked(500).flatMap { imports.existingTripIds(platformId, it) }.toSet()
        val fresh = trips.filter { it.externalId !in known }.distinctBy { it.externalId }
        val batchId = imports.insertBatch(
            ImportBatchEntity(
                platformId = platformId, kind = kind.id, fileHash = fileHash,
                periodStart = trips.minOf { it.date }.toEpochDays(), periodEnd = trips.maxOf { it.date }.toEpochDays(),
                itemCount = fresh.size, importedAt = nowEpochMillis,
            ),
        )
        imports.insertTrips(
            fresh.map {
                TripEntity(
                    platformId = platformId, importBatchId = batchId, externalId = it.externalId, date = it.date.toEpochDays(),
                    startMinute = it.startMinute, fareMinor = it.fareMinor, paymentMethod = it.paymentMethod.id,
                )
            },
        )
        ImportOutcome.Saved(batchId, saved = fresh.size, skipped = trips.size - fresh.size)
    }

    /** Saves a platform's own totals for a period (used to check the daily entries, not counted as income). */
    suspend fun saveSummary(
        platformId: Long,
        kind: ImportKind,
        fileHash: String,
        summary: ParsedSummary,
        nowEpochMillis: Long,
    ): ImportOutcome = transaction {
        if (imports.hasFile(fileHash)) return@transaction ImportOutcome.AlreadyImported
        val start = summary.periodStart.toEpochDays()
        val end = summary.periodEnd.toEpochDays()
        val batchId = imports.insertBatch(
            ImportBatchEntity(
                platformId = platformId, kind = kind.id, fileHash = fileHash, periodStart = start, periodEnd = end,
                itemCount = 1, importedAt = nowEpochMillis,
            ),
        )
        imports.insertSummaries(
            listOf(
                PeriodSummaryEntity(
                    platformId = platformId, importBatchId = batchId, periodStart = start, periodEnd = end,
                    grossFareMinor = summary.grossFareMinor, cancellationMinor = summary.cancellationMinor,
                    tipsMinor = summary.tipsMinor, bonusMinor = summary.bonusMinor, platformFeeMinor = summary.platformFeeMinor,
                    earningsMinor = summary.earningsMinor, distanceMeters = summary.distanceMeters,
                ),
            ),
        )
        ImportOutcome.Saved(batchId, saved = 1, skipped = 0)
    }

    private suspend fun <T> transaction(block: suspend () -> T): T =
        database.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
}
