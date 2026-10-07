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
    /** [app.ridetracker.shared.domain.BodyType] id for the silhouette; added in database version 4. */
    val bodyType: String? = null,
    /** Car colour (ARGB) for the silhouette; added in database version 4. */
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
