package app.ridetracker.ui.vehicle

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.ridetracker.R
import app.ridetracker.shared.data.VehicleDocumentEntity
import app.ridetracker.shared.domain.DocumentKind
import app.ridetracker.shared.domain.DocumentReminders
import kotlinx.datetime.LocalDate

@get:StringRes
val DocumentKind.label: Int
    get() = when (this) {
        DocumentKind.RCA -> R.string.document_rca
        DocumentKind.ITP -> R.string.document_itp
        DocumentKind.ROVINIETA -> R.string.document_rovinieta
        DocumentKind.CASCO -> R.string.document_casco
        DocumentKind.DRIVING_LICENCE -> R.string.document_driving_licence
        DocumentKind.COPIE_CONFORMA -> R.string.document_copie_conforma
        DocumentKind.BADGE_UBER -> R.string.document_badge_uber
        DocumentKind.BADGE_BOLT -> R.string.document_badge_bolt
        DocumentKind.OTHER -> R.string.document_other
    }

val DocumentKind.icon: ImageVector
    get() = when (this) {
        DocumentKind.RCA -> Icons.Outlined.Shield
        DocumentKind.ITP -> Icons.Outlined.Verified
        DocumentKind.ROVINIETA -> Icons.Outlined.Toll
        DocumentKind.CASCO -> Icons.Outlined.VerifiedUser
        DocumentKind.DRIVING_LICENCE -> Icons.Outlined.Badge
        DocumentKind.COPIE_CONFORMA -> Icons.AutoMirrored.Outlined.Assignment
        DocumentKind.BADGE_UBER, DocumentKind.BADGE_BOLT -> Icons.Outlined.LocalTaxi
        DocumentKind.OTHER -> Icons.Outlined.Description
    }

/** The document's own name for "Other", otherwise its kind ("RCA"). */
@Composable
fun documentName(document: VehicleDocumentEntity): String {
    val kind = DocumentKind.fromId(document.kind)
    return document.name?.takeIf { kind == DocumentKind.OTHER && it.isNotBlank() } ?: stringResource(kind.label)
}

/** "45 days left", "Expires today", "Expired 3 days ago". */
@Composable
fun documentStatus(document: VehicleDocumentEntity, today: LocalDate): String {
    val left = DocumentReminders.daysLeft(document.expiryDate, today)
    return when {
        left > 0 -> pluralStringResource(R.plurals.document_days_left, left, left)
        left == 0 -> stringResource(R.string.document_expires_today)
        else -> pluralStringResource(R.plurals.document_expired_ago, -left, -left)
    }
}

/** Red from a week before (and once expired), the accent colour within the month, plain otherwise. */
@Composable
fun documentStatusColor(document: VehicleDocumentEntity, today: LocalDate): Color {
    val left = DocumentReminders.daysLeft(document.expiryDate, today)
    return when {
        left <= 7 -> MaterialTheme.colorScheme.error
        left <= DocumentReminders.ATTENTION_DAYS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
