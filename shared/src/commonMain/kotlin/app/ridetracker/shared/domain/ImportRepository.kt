package app.ridetracker.shared.domain

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.ridetracker.shared.data.AppDatabase
import app.ridetracker.shared.data.ImportBatchEntity
import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.IncomeLineEntity
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.TripEntity
import app.ridetracker.shared.data.TripWithPlatform
import app.ridetracker.shared.domain.importing.BoltDailyParser
import app.ridetracker.shared.domain.importing.ParsedDay
import app.ridetracker.shared.domain.importing.ParsedSummary
import app.ridetracker.shared.domain.importing.ParsedTrip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

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

    fun observeTrips(range: DateRange): Flow<List<TripWithPlatform>> =
        imports.observeTrips(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    fun observeTripDays(): Flow<List<LocalDate>> = imports.observeTripDays().map { days -> days.map(LocalDate::fromEpochDays) }

    /** Platforms' own totals that overlap [range]. */
    fun observeSummaries(range: DateRange): Flow<List<PeriodSummaryEntity>> =
        imports.observeSummaries(range.start.toEpochDays(), range.endInclusive.toEpochDays())

    suspend fun getTrip(id: Long): TripWithPlatform? = imports.getTrip(id)

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

    /**
     * Rebuilds the estimated days of every platform and month that has trips, monthly totals or estimates:
     * days without an exact entry are filled from the platform's own reports (see [IncomeEstimator]).
     * Exact entries are never changed. Run after every import and on start.
     */
    suspend fun refreshEstimates(nowEpochMillis: Long) = transaction {
        val months = buildSet {
            (imports.getTripDays() + imports.getEstimateDays()).forEach { add(it.platformId to Period.Month.containing(LocalDate.fromEpochDays(it.date))) }
            imports.getAllSummaries().forEach { add(it.platformId to Period.Month.containing(LocalDate.fromEpochDays(it.periodStart))) }
        }
        for ((platformId, month) in months) refreshMonth(platformId, month, nowEpochMillis)
    }

    private suspend fun refreshMonth(platformId: Long, month: Period.Month, nowEpochMillis: Long) {
        val start = month.range.start.toEpochDays()
        val end = month.range.endInclusive.toEpochDays()
        imports.deleteEstimates(platformId, start, end)
        val exact = imports.getEntries(platformId, start, end)
            .groupBy { LocalDate.fromEpochDays(it.date) }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        val fares = imports.getTripFaresByDay(platformId, start, end).associate { LocalDate.fromEpochDays(it.date) to it.totalMinor }
        val summaries = imports.getSummaries(platformId, start, end)
        val monthly = summaries.firstOrNull { it.earningsMinor != null }
        val pdf = summaries.firstOrNull { it.earningsMinor == null && it.grossFareMinor != null }
        val totals = imports.getFareAndCommission(platformId).associate { it.kind to it.totalMinor }
        val keepRate = IncomeEstimator.keepRate(totals[IncomeLineKind.FARE.id] ?: 0, totals[IncomeLineKind.COMMISSION.id] ?: 0)
        val result = IncomeEstimator.estimate(
            exactByDay = exact,
            tripFaresByDay = fares,
            monthlyEarningsMinor = monthly?.earningsMinor,
            summaryFaresMinor = pdf?.grossFareMinor,
            summaryCancellationMinor = pdf?.cancellationMinor,
            summaryTipsMinor = pdf?.tipsMinor,
            keepRate = keepRate,
            lastDay = month.range.endInclusive,
        )
        result.days.forEach { day ->
            entries.insert(
                IncomeEntryEntity(
                    platformId = platformId,
                    amountMinor = day.amountMinor,
                    date = day.date.toEpochDays(),
                    createdAt = nowEpochMillis,
                    source = IncomeSource.ESTIMATE.id,
                ),
            )
        }
    }

    /**
     * Lines saved as "other" because an older reader did not know their label (e.g. an accent misread
     * by OCR) get their proper kind once the reader learns it. Safe to run on every start.
     */
    suspend fun reclassifyLines() {
        for (line in imports.getLinesOfKind(IncomeLineKind.OTHER.id)) {
            val kind = BoltDailyParser.lineKind(line.label ?: continue)
            if (kind != IncomeLineKind.OTHER) imports.setLineKind(line.id, kind.id)
        }
    }

    private suspend fun <T> transaction(block: suspend () -> T): T =
        database.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
}
