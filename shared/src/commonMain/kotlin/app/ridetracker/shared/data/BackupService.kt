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
) {
    companion object {
        const val FORMAT = "ridetracker-backup"

        /** 1: income only (0.0.4–0.0.5). 2: + expenses (0.0.6). 3: + vehicle and odometer (0.0.7). */
        const val FORMAT_VERSION = 3
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
                BackupVehicle(it.id, it.name, it.year, it.fuelType, it.consumptionCenti, it.fuelPriceMinor)
            },
            odometerReadings = database.vehicleDao().getAllReadings().map {
                BackupOdometerReading(it.id, it.vehicleId, LocalDate.fromEpochDays(it.date).toString(), it.km, it.createdAt)
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
        val vehicles = file.vehicles.map { VehicleEntity(it.id, it.name, it.year, it.fuelType, it.consumptionCenti, it.fuelPriceMinor) }
        val readings = file.odometerReadings.map {
            OdometerReadingEntity(it.id, it.vehicleId, LocalDate.parse(it.date).toEpochDays(), it.km, it.createdAtEpochMillis)
        }
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                database.vehicleDao().deleteAllReadings()
                database.vehicleDao().deleteAll()
                database.expenseDao().deleteAll()
                database.incomeEntryDao().deleteAll()
                database.platformDao().deleteAll()
                database.platformDao().insertAll(platforms)
                database.incomeEntryDao().insertAll(entries)
                database.expenseDao().insertAll(expenses)
                database.vehicleDao().insertAll(vehicles)
                database.vehicleDao().insertReadings(readings)
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
                database.vehicleDao().deleteAllReadings()
                database.vehicleDao().deleteAll()
                database.expenseDao().deleteAll()
                database.incomeEntryDao().deleteAll()
                database.platformDao().deleteAll()
                database.platformDao().insertAll(defaultPlatforms)
            }
        }
        settingsRepository.clear()
    }
}
