package app.ridetracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.ridetracker.AppContainer
import app.ridetracker.MainActivity
import app.ridetracker.R
import app.ridetracker.shared.domain.DocumentKind
import app.ridetracker.shared.domain.DocumentReminders
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.vehicle.label
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Locale
import kotlin.time.Clock

/** Reminds about car documents 30 and 7 days before they expire, and on the day. Runs with the daily check. */
object DocumentNotifier {
    const val CHANNEL_ID = "car_documents"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.document_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.document_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Ids apart from the recurring expenses' (which use the rule id). */
    private fun notificationId(documentId: Long): Int = 1_000_000_000 + (documentId % 100_000_000).toInt()

    suspend fun notifyDue(context: Context, container: AppContainer) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val dates = DateFormats(Locale.getDefault())
        val manager = NotificationManagerCompat.from(context)
        container.vehicleRepository.getAllDocuments().forEach { document ->
            val stage = DocumentReminders.stageDue(document.expiryDate, document.notifiedStage, today) ?: return@forEach
            val kind = DocumentKind.fromId(document.kind)
            val name = document.name?.takeIf { kind == DocumentKind.OTHER && it.isNotBlank() } ?: context.getString(kind.label)
            val id = notificationId(document.id)
            val open = PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = if (stage == 0) {
                context.getString(R.string.document_notification_today, name)
            } else {
                context.getString(R.string.document_notification_soon, name, dates.day(LocalDate.fromEpochDays(document.expiryDate)))
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ring)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.document_notification_body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission") // checked above
            manager.notify(id, notification)
            container.vehicleRepository.markDocumentNotified(document, stage)
        }
    }
}
