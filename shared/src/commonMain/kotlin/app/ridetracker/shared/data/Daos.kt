package app.ridetracker.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {
    @Query("SELECT * FROM platform ORDER BY archived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<PlatformEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM platform")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(platform: PlatformEntity): Long

    @Update
    suspend fun update(platform: PlatformEntity)

    @Query("SELECT * FROM platform ORDER BY id")
    suspend fun getAll(): List<PlatformEntity>

    @Insert
    suspend fun insertAll(platforms: List<PlatformEntity>)

    @Query("DELETE FROM platform")
    suspend fun deleteAll()
}

@Dao
interface IncomeEntryDao {
    @Query(
        """
        SELECT e.id, e.platformId, e.amountMinor, e.date, e.note, e.createdAt,
               p.name AS platformName, p.colorArgb AS platformColorArgb
        FROM income_entry e JOIN platform p ON p.id = e.platformId
        WHERE e.date BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY e.date DESC, e.createdAt DESC
        """,
    )
    fun observeInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<EntryWithPlatform>>

    @Query(
        """
        SELECT p.id AS platformId, p.name AS name, p.colorArgb AS colorArgb, SUM(e.amountMinor) AS totalMinor
        FROM income_entry e JOIN platform p ON p.id = e.platformId
        WHERE e.date BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY p.id
        ORDER BY totalMinor DESC
        """,
    )
    fun observeTotalsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<PlatformTotal>>

    @Query("SELECT * FROM income_entry WHERE id = :id")
    suspend fun getById(id: Long): IncomeEntryEntity?

    @Insert
    suspend fun insert(entry: IncomeEntryEntity): Long

    @Update
    suspend fun update(entry: IncomeEntryEntity)

    @Query("DELETE FROM income_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT e.id, e.platformId, e.amountMinor, e.date, e.note, e.createdAt,
               p.name AS platformName, p.colorArgb AS platformColorArgb
        FROM income_entry e JOIN platform p ON p.id = e.platformId
        ORDER BY e.date DESC, e.createdAt DESC
        """,
    )
    fun observeAll(): Flow<List<EntryWithPlatform>>

    @Query("SELECT * FROM income_entry ORDER BY id")
    suspend fun getAll(): List<IncomeEntryEntity>

    @Insert
    suspend fun insertAll(entries: List<IncomeEntryEntity>)

    @Query("DELETE FROM income_entry")
    suspend fun deleteAll()
}
