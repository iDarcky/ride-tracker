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
