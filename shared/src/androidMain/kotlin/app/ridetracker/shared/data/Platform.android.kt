package app.ridetracker.shared.data

import android.content.Context
import androidx.room.Room

fun createAppDatabase(context: Context): AppDatabase {
    val appContext = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(AppDatabase.FILE_NAME).absolutePath,
    ).buildAppDatabase()
}

fun createSettingsRepository(context: Context): SettingsRepository =
    SettingsRepository.create(context.applicationContext.filesDir.resolve(SettingsRepository.FILE_NAME).absolutePath)
