package app.ridetracker.shared.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.shared.domain.ThemeMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Whole-app backup as a JSON file. The format is independent of the database schema
 * (dates are ISO strings, enums are their persisted ids), so a backup from any version
 * restores into any later version. Bump [BackupFile.FORMAT_VERSION] only for incompatible
 * changes, and keep reading older versions.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    val appVersion: String,
    val createdAtEpochMillis: Long,
    val settings: BackupSettings,
    val platforms: List<BackupPlatform>,
    @SerialName("incomeEntries") val entries: List<BackupIncomeEntry>,
    /** Added in format 2 (0.0.6); empty when restoring a format-1 backup. */
    val expenses: List<BackupExpense> = emptyList(),
    /** Added in format 3 (0.0.7). */
    val vehicles: List<BackupVehicle> = emptyList(),
    val odometerReadings: List<BackupOdometerReading> = emptyList(),
    /** Added in format 5 (0.0.9). */
    val recurringExpenses: List<BackupRecurringExpense> = emptyList(),
    /** Added in format 6 (0.1.0). */
    val incomeLines: List<BackupIncomeLine> = emptyList(),
    val importBatches: List<BackupImportBatch> = emptyList(),
    val trips: List<BackupTrip> = emptyList(),
    val periodSummaries: List<BackupPeriodSummary> = emptyList(),
) {
    companion object {
        const val FORMAT = "ridetracker-backup"

        /** 1: income only (0.0.4–0.0.5). 2: + expenses (0.0.6). 3: + vehicle and odometer (0.0.7). 4: + body type and colour (0.0.8). 5: + recurring expenses (0.0.9).
         *  6: + income details, income lines, imports, trips and period summaries (0.1.0). */
        const val FORMAT_VERSION = 6
    }
}

@Serializable
data class BackupSettings(
    val country: String? = null,
    val otherCurrencyCode: String? = null,
    val firstDayOfWeek: Int = 1,
    val themeMode: String? = null,
    val drivingType: String? = null,
)

@Serializable
data class BackupPlatform(
    val id: Long,
    val name: String,
    val colorArgb: Long,
    val sortOrder: Int,
    val archived: Boolean,
)

@Serializable
data class BackupIncomeEntry(
    val id: Long,
    val platformId: Long,
    val amountMinor: Long,
    /** ISO date, e.g. 2026-10-07. */
    val date: String,
    val note: String? = null,
    val createdAtEpochMillis: Long,
    /** Added in format 6. */
    val source: String = "manual",
    val importBatchId: Long? = null,
    val cashCollectedMinor: Long? = null,
    val onlineMinutes: Int? = null,
    val tripCount: Int? = null,
)

@Serializable
data class BackupIncomeLine(
    val id: Long,
    val entryId: Long,
    val kind: String,
    val amountMinor: Long,
    val inCash: Boolean = false,
    val label: String? = null,
)

@Serializable
data class BackupImportBatch(
    val id: Long,
    val platformId: Long,
    val kind: String,
    val fileHash: String,
    /** ISO dates. */
    val periodStart: String,
    val periodEnd: String,
    val itemCount: Int,
    val importedAtEpochMillis: Long,
)

@Serializable
data class BackupTrip(
    val id: Long,
    val platformId: Long,
    val importBatchId: Long,
    val externalId: String,
    /** ISO date. */
    val date: String,
    val startMinute: Int,
    val fareMinor: Long,
    val paymentMethod: String,
    val distanceMeters: Long? = null,
    val durationSeconds: Long? = null,
)

@Serializable
data class BackupPeriodSummary(
    val id: Long,
    val platformId: Long,
    val importBatchId: Long,
    /** ISO dates. */
    val periodStart: String,
    val periodEnd: String,
    val grossFareMinor: Long? = null,
    val cancellationMinor: Long? = null,
    val tipsMinor: Long? = null,
    val bonusMinor: Long? = null,
    val platformFeeMinor: Long? = null,
    val earningsMinor: Long? = null,
    val distanceMeters: Long? = null,
    val tripCount: Int? = null,
    val onlineMinutes: Int? = null,
)

@Serializable
data class BackupExpense(
    val id: Long,
    val amountMinor: Long,
    /** ISO date, e.g. 2026-10-07. */
    val date: String,
    /** Category id, e.g. "fuel". */
    val category: String,
    val note: String? = null,
    val createdAtEpochMillis: Long,
)

@Serializable
data class BackupVehicle(
    val id: Long,
    val name: String,
    val year: Int? = null,
    val fuelType: String,
    val consumptionCenti: Long? = null,
    val fuelPriceMinor: Long? = null,
    val bodyType: String? = null,
    val colorArgb: Long? = null,
)

@Serializable
data class BackupOdometerReading(
    val id: Long,
    val vehicleId: Long,
    /** ISO date. */
    val date: String,
    val km: Long,
    val createdAtEpochMillis: Long,
)

@Serializable
data class BackupRecurringExpense(
    val id: Long,
    val amountMinor: Long,
    val category: String,
    val note: String? = null,
    val frequency: String,
    /** ISO dates. */
    val anchorDate: String,
    val nextDueDate: String,
    val endDate: String? = null,
    val createdAtEpochMillis: Long,
)

/** Thrown when a file is not a backup from this app, or from a newer, unsupported format. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BackupSummary(val platforms: Int, val entries: Int, val expenses: Int)

class BackupService(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // Newer backups may add fields; older apps skip them.
        encodeDefaults = true
    }

    suspend fun export(appVersion: String, nowEpochMillis: Long): String {
        val settings = settingsRepository.current()
        val file = BackupFile(
            appVersion = appVersion,
            createdAtEpochMillis = nowEpochMillis,
            settings = BackupSettings(
                country = settings.country?.id,
                otherCurrencyCode = settings.otherCurrencyCode,
                firstDayOfWeek = settings.firstDayOfWeek.isoDayNumber,
                themeMode = settings.themeMode.id,
                drivingType = settings.drivingType?.id,
            ),
            platforms = database.platformDao().getAll().map {
                BackupPlatform(it.id, it.name, it.colorArgb, it.sortOrder, it.archived)
            },
            entries = database.incomeEntryDao().getAll().map {
                BackupIncomeEntry(
                    id = it.id,
                    platformId = it.platformId,
                    amountMinor = it.amountMinor,
                    date = LocalDate.fromEpochDays(it.date).toString(),
                    note = it.note,
                    createdAtEpochMillis = it.createdAt,
                    source = it.source,
                    importBatchId = it.importBatchId,
                    cashCollectedMinor = it.cashCollectedMinor,
                    onlineMinutes = it.onlineMinutes,
                    tripCount = it.tripCount,
                )
            },
            expenses = database.expenseDao().getAll().map {
                BackupExpense(
                    id = it.id,
                    amountMinor = it.amountMinor,
                    date = LocalDate.fromEpochDays(it.date).toString(),
                    category = it.category,
                    note = it.note,
                    createdAtEpochMillis = it.createdAt,
                )
            },
            vehicles = database.vehicleDao().getAll().map {
                BackupVehicle(it.id, it.name, it.year, it.fuelType, it.consumptionCenti, it.fuelPriceMinor, it.bodyType, it.colorArgb)
            },
            odometerReadings = database.vehicleDao().getAllReadings().map {
                BackupOdometerReading(it.id, it.vehicleId, LocalDate.fromEpochDays(it.date).toString(), it.km, it.createdAt)
            },
            recurringExpenses = database.recurringExpenseDao().getAll().map {
                BackupRecurringExpense(
                    id = it.id,
                    amountMinor = it.amountMinor,
                    category = it.category,
                    note = it.note,
                    frequency = it.frequency,
                    anchorDate = LocalDate.fromEpochDays(it.anchorDate).toString(),
                    nextDueDate = LocalDate.fromEpochDays(it.nextDueDate).toString(),
                    endDate = it.endDate?.let { d -> LocalDate.fromEpochDays(d).toString() },
                    createdAtEpochMillis = it.createdAt,
                )
            },
            incomeLines = database.importDao().getAllLines().map {
                BackupIncomeLine(it.id, it.entryId, it.kind, it.amountMinor, it.inCash, it.label)
            },
            importBatches = database.importDao().getAllBatches().map {
                BackupImportBatch(it.id, it.platformId, it.kind, it.fileHash, day(it.periodStart), day(it.periodEnd), it.itemCount, it.importedAt)
            },
            trips = database.importDao().getAllTrips().map {
                BackupTrip(
                    it.id, it.platformId, it.importBatchId, it.externalId, day(it.date), it.startMinute,
                    it.fareMinor, it.paymentMethod, it.distanceMeters, it.durationSeconds,
                )
            },
            periodSummaries = database.importDao().getAllSummaries().map {
                BackupPeriodSummary(
                    it.id, it.platformId, it.importBatchId, day(it.periodStart), day(it.periodEnd), it.grossFareMinor,
                    it.cancellationMinor, it.tipsMinor, it.bonusMinor, it.platformFeeMinor, it.earningsMinor,
                    it.distanceMeters, it.tripCount, it.onlineMinutes,
                )
            },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** Parses and checks a backup without changing anything. */
    fun read(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw InvalidBackupException("Not a Ride Tracker backup", e)
        }
        if (file.format != BackupFile.FORMAT) throw InvalidBackupException("Not a Ride Tracker backup")
        if (file.formatVersion > BackupFile.FORMAT_VERSION) {
            throw InvalidBackupException("Backup is from a newer app version")
        }
        val platformIds = file.platforms.map { it.id }.toSet()
        if (file.entries.any { it.platformId !in platformIds }) throw InvalidBackupException("Backup is damaged")
        val vehicleIds = file.vehicles.map { it.id }.toSet()
        if (file.odometerReadings.any { it.vehicleId !in vehicleIds }) throw InvalidBackupException("Backup is damaged")
        val entryIds = file.entries.map { it.id }.toSet()
        val batchIds = file.importBatches.map { it.id }.toSet()
        if (file.incomeLines.any { it.entryId !in entryIds } ||
            file.importBatches.any { it.platformId !in platformIds } ||
            file.trips.any { it.platformId !in platformIds || it.importBatchId !in batchIds } ||
            file.periodSummaries.any { it.platformId !in platformIds || it.importBatchId !in batchIds }
        ) {
            throw InvalidBackupException("Backup is damaged")
        }
        return file
    }

    /** Replaces all data and settings with [file], in one transaction. */
    suspend fun restore(file: BackupFile): BackupSummary {
        val entries = file.entries.map {
            IncomeEntryEntity(
                id = it.id,
                platformId = it.platformId,
                amountMinor = it.amountMinor,
                date = LocalDate.parse(it.date).toEpochDays(),
                note = it.note,
                createdAt = it.createdAtEpochMillis,
                source = it.source,
                importBatchId = it.importBatchId,
                cashCollectedMinor = it.cashCollectedMinor,
                onlineMinutes = it.onlineMinutes,
                tripCount = it.tripCount,
            )
        }
        val lines = file.incomeLines.map { IncomeLineEntity(it.id, it.entryId, it.kind, it.amountMinor, it.inCash, it.label) }
        val batches = file.importBatches.map {
            ImportBatchEntity(it.id, it.platformId, it.kind, it.fileHash, epochDay(it.periodStart), epochDay(it.periodEnd), it.itemCount, it.importedAtEpochMillis)
        }
        val trips = file.trips.map {
            TripEntity(
                it.id, it.platformId, it.importBatchId, it.externalId, epochDay(it.date), it.startMinute,
                it.fareMinor, it.paymentMethod, it.distanceMeters, it.durationSeconds,
            )
        }
        val summaries = file.periodSummaries.map {
            PeriodSummaryEntity(
                it.id, it.platformId, it.importBatchId, epochDay(it.periodStart), epochDay(it.periodEnd), it.grossFareMinor,
                it.cancellationMinor, it.tipsMinor, it.bonusMinor, it.platformFeeMinor, it.earningsMinor,
                it.distanceMeters, it.tripCount, it.onlineMinutes,
            )
        }
        val platforms = file.platforms.map { PlatformEntity(it.id, it.name, it.colorArgb, it.sortOrder, it.archived) }
        val expenses = file.expenses.map {
            ExpenseEntity(
                id = it.id,
                amountMinor = it.amountMinor,
                date = LocalDate.parse(it.date).toEpochDays(),
                category = it.category,
                note = it.note,
                createdAt = it.createdAtEpochMillis,
            )
        }
        val vehicles = file.vehicles.map { VehicleEntity(it.id, it.name, it.year, it.fuelType, it.consumptionCenti, it.fuelPriceMinor, it.bodyType, it.colorArgb) }
        val readings = file.odometerReadings.map {
            OdometerReadingEntity(it.id, it.vehicleId, LocalDate.parse(it.date).toEpochDays(), it.km, it.createdAtEpochMillis)
        }
        val recurring = file.recurringExpenses.map {
            RecurringExpenseEntity(
                id = it.id,
                amountMinor = it.amountMinor,
                category = it.category,
                note = it.note,
                frequency = it.frequency,
                anchorDate = LocalDate.parse(it.anchorDate).toEpochDays(),
                nextDueDate = LocalDate.parse(it.nextDueDate).toEpochDays(),
                endDate = it.endDate?.let { d -> LocalDate.parse(d).toEpochDays() },
                createdAt = it.createdAtEpochMillis,
            )
        }
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                deleteAllRows()
                database.platformDao().insertAll(platforms)
                database.importDao().insertBatches(batches)
                database.incomeEntryDao().insertAll(entries)
                database.importDao().insertLines(lines)
                database.importDao().insertTrips(trips)
                database.importDao().insertSummaries(summaries)
                database.expenseDao().insertAll(expenses)
                database.vehicleDao().insertAll(vehicles)
                database.vehicleDao().insertReadings(readings)
                database.recurringExpenseDao().insertAll(recurring)
            }
        }
        val s = file.settings
        settingsRepository.replaceAll(
            AppSettings(
                country = Country.fromId(s.country),
                otherCurrencyCode = s.otherCurrencyCode,
                firstDayOfWeek = DayOfWeek(s.firstDayOfWeek.coerceIn(1, 7)),
                themeMode = ThemeMode.fromId(s.themeMode),
                drivingType = DrivingType.fromId(s.drivingType),
            ),
        )
        return BackupSummary(platforms.size, entries.size, expenses.size)
    }

    /** Deletes all entries, apps and settings; the default apps come back and onboarding restarts. */
    suspend fun eraseAll() {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                deleteAllRows()
                database.platformDao().insertAll(defaultPlatforms)
            }
        }
        settingsRepository.clear()
    }

    /** Children before parents, so foreign keys never block. Call inside a transaction. */
    private suspend fun deleteAllRows() {
        database.importDao().deleteAllSummaries()
        database.importDao().deleteAllTrips()
        database.importDao().deleteAllLines()
        database.recurringExpenseDao().deleteAll()
        database.vehicleDao().deleteAllReadings()
        database.vehicleDao().deleteAll()
        database.expenseDao().deleteAll()
        database.incomeEntryDao().deleteAll()
        database.importDao().deleteAllBatches()
        database.platformDao().deleteAll()
    }

    private fun day(epochDay: Long): String = LocalDate.fromEpochDays(epochDay).toString()

    private fun epochDay(iso: String): Long = LocalDate.parse(iso).toEpochDays()
}
