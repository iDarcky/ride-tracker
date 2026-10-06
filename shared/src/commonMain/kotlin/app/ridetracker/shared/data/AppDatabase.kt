package app.ridetracker.shared.data

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
    entities = [PlatformEntity::class, IncomeEntryEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun platformDao(): PlatformDao
    abstract fun incomeEntryDao(): IncomeEntryDao

    companion object {
        const val FILE_NAME = "ridetracker.db"
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Seeds the default platforms the first time the database is created (never on upgrade). */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        connection.execSQL(
            "INSERT INTO platform (name, colorArgb, sortOrder, archived) VALUES " +
                "('Uber', ${0xFF000000}, 0, 0), ('Bolt', ${0xFF34D186}, 1, 0)",
        )
    }
}

fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(SeedCallback)
        .build()
