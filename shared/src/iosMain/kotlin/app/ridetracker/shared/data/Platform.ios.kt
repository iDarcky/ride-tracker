package app.ridetracker.shared.data

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun documentsDirectory(): String {
    val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path)
}

fun createAppDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = documentsDirectory() + "/" + AppDatabase.FILE_NAME)
        .buildAppDatabase()

fun createSettingsRepository(): SettingsRepository =
    SettingsRepository.create(documentsDirectory() + "/" + SettingsRepository.FILE_NAME)
