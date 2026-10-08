package app.ridetracker.shared.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A ride-sharing app the driver earns from (Uber, Bolt, or anything the user adds). */
@Entity(tableName = "platform")
data class PlatformEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB colour used for the platform's letter badge. */
    val colorArgb: Long,
    val sortOrder: Int,
    /** Archived platforms are hidden from the entry form but keep their history. */
    val archived: Boolean = false,
)

@Entity(
    tableName = "income_entry",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("platformId"), Index("date")],
)
data class IncomeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: Long,
    /** Amount in minor units of the app currency (e.g. cents). Never a float. */
    val amountMinor: Long,
    /** Day the income was earned, as epoch days (kotlinx.datetime.LocalDate.toEpochDays()). */
    val date: Long,
    val note: String? = null,
    /** Creation instant in epoch milliseconds. */
    @ColumnInfo(defaultValue = "0") val createdAt: Long,
    /** [app.ridetracker.shared.domain.IncomeSource] id. Added in version 6. */
    @ColumnInfo(defaultValue = "manual") val source: String = "manual",
    /** The import that created or last updated this entry; null when typed in. */
    val importBatchId: Long? = null,
    /** Cash the driver collected from riders that day (Bolt's "Cash in hand"). Null if unknown. */
    val cashCollectedMinor: Long? = null,
    val onlineMinutes: Int? = null,
    val tripCount: Int? = null,
)

/**
 * One line of an entry's breakdown (fares, tips, commission…). Signed: deductions are negative.
 * Entries typed in with a single amount have no lines.
 */
@Entity(
    tableName = "income_line",
    foreignKeys = [ForeignKey(entity = IncomeEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")],
)
data class IncomeLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    /** [app.ridetracker.shared.domain.IncomeLineKind] id. */
    val kind: String,
    val amountMinor: Long,
    /** True for the "Cash income" group of a Bolt breakdown. */
    val inCash: Boolean = false,
    /** The label as the report wrote it (e.g. "Campaigns"), kept for display. */
    val label: String? = null,
)

/** One imported report or screenshot. [fileHash] stops the same file being imported twice. */
@Entity(
    tableName = "import_batch",
    foreignKeys = [ForeignKey(entity = PlatformEntity::class, parentColumns = ["id"], childColumns = ["platformId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("platformId"), Index(value = ["fileHash"], unique = true)],
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: Long,
    /** [app.ridetracker.shared.domain.ImportKind] id. */
    val kind: String,
    /** SHA-256 of the file's bytes, hex. */
    val fileHash: String,
    /** First and last day the report covers (epoch days). */
    val periodStart: Long,
    val periodEnd: Long,
    /** Entries, trips or summaries the import saved. */
    val itemCount: Int,
    val importedAt: Long,
)

/** A single ride, from a per-trip report. Never holds rider names or addresses. */
@Entity(
    tableName = "trip",
    foreignKeys = [
        ForeignKey(entity = PlatformEntity::class, parentColumns = ["id"], childColumns = ["platformId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ImportBatchEntity::class, parentColumns = ["id"], childColumns = ["importBatchId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("importBatchId"), Index("date"), Index(value = ["platformId", "externalId"], unique = true)],
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: Long,
    val importBatchId: Long,
    /** The platform's own id for the ride (e.g. Bolt invoice number), to skip duplicates. */
    val externalId: String,
    /** Day of the ride (epoch days) and local start time in minutes after midnight. */
    val date: Long,
    val startMinute: Int,
    /** What the rider paid. */
    val fareMinor: Long,
    /** [app.ridetracker.shared.domain.PaymentMethod] id. */
    val paymentMethod: String,
    val distanceMeters: Long? = null,
    val durationSeconds: Long? = null,
)

/**
 * A platform's own totals for a week or month (Bolt monthly summary, Uber weekly payments).
 * Not counted as income: used to check that the daily entries add up.
 */
@Entity(
    tableName = "period_summary",
    foreignKeys = [
        ForeignKey(entity = PlatformEntity::class, parentColumns = ["id"], childColumns = ["platformId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ImportBatchEntity::class, parentColumns = ["id"], childColumns = ["importBatchId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("platformId"), Index("importBatchId")],
)
data class PeriodSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: Long,
    val importBatchId: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val grossFareMinor: Long? = null,
    val cancellationMinor: Long? = null,
    val tipsMinor: Long? = null,
    val bonusMinor: Long? = null,
    /** Commission / service fee, negative. */
    val platformFeeMinor: Long? = null,
    /** What the platform says the driver earned. */
    val earningsMinor: Long? = null,
    val distanceMeters: Long? = null,
    val tripCount: Int? = null,
    val onlineMinutes: Int? = null,
)

/** Money spent: fuel, repairs, accountant… [category] is an [app.ridetracker.shared.domain.ExpenseCategory] id. */
@Entity(tableName = "expense", indices = [Index("date")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Amount in minor units of the app currency. Never a float. */
    val amountMinor: Long,
    /** Day of the expense, as epoch days. */
    val date: Long,
    val category: String,
    val note: String? = null,
    /** Creation instant in epoch milliseconds. */
    val createdAt: Long,
)

/**
 * A repeating expense (leasing, accountant, insurance…). Nothing is added automatically:
 * when [nextDueDate] arrives the app asks, then moves [nextDueDate] to the following occurrence.
 */
@Entity(tableName = "recurring_expense")
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    /** [app.ridetracker.shared.domain.ExpenseCategory] id. */
    val category: String,
    val note: String? = null,
    /** [app.ridetracker.shared.domain.Frequency] id. */
    val frequency: String,
    /** First occurrence (epoch days); every later date is computed from it. */
    val anchorDate: Long,
    /** The next occurrence not yet added or skipped (epoch days). */
    val nextDueDate: Long,
    /** Last occurrence allowed (epoch days); null = no end. */
    val endDate: Long? = null,
    /** The due date we last sent a notification for, so each one is notified once. */
    val notifiedDueDate: Long? = null,
    val createdAt: Long,
)

/** The driver's car (one for now; the model allows more later). */
@Entity(tableName = "vehicle")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val year: Int? = null,
    /** [app.ridetracker.shared.domain.FuelType] id. */
    val fuelType: String,
    /** Litres (or kWh) per 100 km × 100, e.g. 700 = 7.0 L/100 km. Null if unknown. */
    val consumptionCenti: Long? = null,
    /** Price per litre (or kWh) in minor units. Null if unknown. */
    val fuelPriceMinor: Long? = null,
    /** Unused: car silhouettes were tried in 0.0.8 and removed. Kept so database version 4 stays valid. */
    val bodyType: String? = null,
    /** Unused, see [bodyType]. */
    val colorArgb: Long? = null,
)

@Entity(
    tableName = "odometer_reading",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index("date")],
)
data class OdometerReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    /** Day of the reading, as epoch days. */
    val date: Long,
    val km: Long,
    val createdAt: Long,
)

/** An income entry joined with its platform, for lists. */
data class EntryWithPlatform(
    val id: Long,
    val platformId: Long,
    val amountMinor: Long,
    val date: Long,
    val note: String?,
    val createdAt: Long,
    val platformName: String,
    val platformColorArgb: Long,
)

/** Sum of income for one platform over a date range. */
data class PlatformTotal(
    val platformId: Long,
    val name: String,
    val colorArgb: Long,
    val totalMinor: Long,
)
