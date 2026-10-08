package app.ridetracker.shared.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Schema history lives in `shared/schemas/`.
 *
 * Upgrading rule: never use destructive migrations. Every version bump must add an
 * AutoMigration or a manual Migration so installs over the previous app keep all data.
 */
@Database(
    entities = [
        PlatformEntity::class, IncomeEntryEntity::class, ExpenseEntity::class,
        VehicleEntity::class, OdometerReadingEntity::class, RecurringExpenseEntity::class,
        IncomeLineEntity::class, ImportBatchEntity::class, TripEntity::class, PeriodSummaryEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // 0.0.6: expense table
        AutoMigration(from = 2, to = 3), // 0.0.7: vehicle and odometer tables
        AutoMigration(from = 3, to = 4), // 0.0.8: vehicle body type and colour
        AutoMigration(from = 4, to = 5), // 0.0.9: recurring expenses
        AutoMigration(from = 5, to = 6), // 0.1.0: income details, imports, trips, period summaries
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun platformDao(): PlatformDao
    abstract fun incomeEntryDao(): IncomeEntryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun importDao(): ImportDao

    companion object {
        const val FILE_NAME = "ridetracker.db"
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Apps every new install (and every reset) starts with. */
val defaultPlatforms: List<PlatformEntity> = listOf(
    PlatformEntity(name = "Uber", colorArgb = 0xFF000000, sortOrder = 0),
    PlatformEntity(name = "Bolt", colorArgb = 0xFF34D186, sortOrder = 1),
)

/** Seeds the default platforms the first time the database is created (never on upgrade). */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        val values = defaultPlatforms.joinToString { "('${it.name}', ${it.colorArgb}, ${it.sortOrder}, 0)" }
        connection.execSQL("INSERT INTO platform (name, colorArgb, sortOrder, archived) VALUES $values")
    }
}

fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(SeedCallback)
        .build()
