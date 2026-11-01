package de.felitendo.kontor.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.felitendo.kontor.MainActivity
import de.felitendo.kontor.R
import de.felitendo.kontor.container
import de.felitendo.kontor.data.model.FixedDeposit
import de.felitendo.kontor.domain.maturityDay
import de.felitendo.kontor.domain.maturityValue
import de.felitendo.kontor.domain.today
import de.felitendo.kontor.ui.Fmt
import java.util.concurrent.TimeUnit

object Work {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_UPDATES = "updates"

    fun schedule(context: Context) {
        createChannels(context)
        val wm = WorkManager.getInstance(context)
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            "refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS).setConstraints(online).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "daily",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyWorker>(12, TimeUnit.HOURS).build(),
        )
    }

    private fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Fälligkeiten", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Erinnert, wenn ein Festgeld ausläuft"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, "Updates", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Neue Kontor-Versionen"
        })
    }

    fun notify(context: Context, id: Int, channel: String, title: String, text: String, extra: String? = null) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extra?.let { putExtra(MainActivity.EXTRA_OPEN, it) }
        }
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_kontor)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ok = applicationContext.container.refresh(maxAgeMillis = 20 * 60_000L)
        return if (ok) Result.success() else Result.retry()
    }
}

/** Festgeld reminders and the background update check. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val settings = c.settings.current()

        if (settings.maturityReminders) {
            val now = today()
            val due = c.portfolio.current().assets.filterIsInstance<FixedDeposit>().mapNotNull { fd ->
                val end = fd.maturityDay ?: return@mapNotNull null
                val daysLeft = end - now
                val key = when {
                    daysLeft in 0..7 -> "${fd.id}:7"
                    daysLeft in 8..30 -> "${fd.id}:30"
                    else -> null
                } ?: return@mapNotNull null
                if (key in settings.notifiedMaturities) null else Triple(fd, daysLeft, key)
            }
            due.forEach { (fd, daysLeft, key) ->
                val whenText = when (daysLeft) {
                    0L -> "heute"
                    1L -> "morgen"
                    else -> "in $daysLeft Tagen"
                }
                Work.notify(
                    applicationContext, key.hashCode(), Work.CHANNEL_REMINDERS,
                    "Festgeld bei ${fd.bank.name} läuft $whenText aus",
                    "Am ${Fmt.date(fd.maturityDay!!)} werden voraussichtlich ${Fmt.eur(fd.maturityValue ?: fd.amount)} frei. Zeit, über die Anschlussanlage nachzudenken.",
                    extra = "asset:${fd.id}",
                )
            }
            if (due.isNotEmpty()) c.settings.update { s -> s.copy(notifiedMaturities = s.notifiedMaturities + due.map { it.third }) }
        }

        if (settings.autoUpdateCheck) {
            val channel = settings.updateChannel ?: c.updater.buildChannel
            val release = runCatching { c.updater.check(channel) }.getOrNull()
            if (release != null && release.versionCode != settings.dismissedUpdateCode) {
                Work.notify(
                    applicationContext, 4711, Work.CHANNEL_UPDATES,
                    "Kontor ${release.versionName} ist da",
                    "Tippe, um das Update zu installieren.",
                    extra = "update",
                )
            }
        }
        return Result.success()
    }
}
