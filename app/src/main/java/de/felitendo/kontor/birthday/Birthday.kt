package de.felitendo.kontor.birthday

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.felitendo.kontor.work.Work
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Helli's birthday (19 November). On that day Kontor throws a party: confetti when the app
 * opens, a greeting from Felix on the home screen, the lock screen, the widget and a
 * notification in the morning. Every other day the app is unchanged.
 */
object Birthday {
    const val NAME = "Helli"
    const val FROM = "Felix"
    const val BORN = 1971
    private const val MONTH = 11
    private const val DAY = 19

    private const val CHANNEL = "greetings"
    private const val NOTIFICATION_ID = 1911

    /**
     * Seven taps on "Version" in the settings switch the birthday on for any day, so Felix can try
     * it out (and feel the vibrations) on his own phone. Survives restarts until switched off.
     */
    @Volatile
    var testMode = false
        private set

    /** Reads [testMode]; called once when the app starts. */
    fun init(context: Context) {
        testMode = prefs(context).getBoolean("test", false)
    }

    fun setTestMode(context: Context, on: Boolean) {
        testMode = on
        prefs(context).edit().putBoolean("test", on).apply()
    }

    /** The real 19 November, ignoring the test mode. */
    private fun isRealBirthday(date: LocalDate = LocalDate.now()): Boolean = date.monthValue == MONTH && date.dayOfMonth == DAY

    /** Whether the app celebrates right now: on the birthday, or any day in test mode. */
    fun isToday(): Boolean = testMode || isRealBirthday()

    /** The age Helli turns this year (55 in 2026). */
    fun age(date: LocalDate = LocalDate.now()): Int = date.year - BORN

    /** 9 o'clock on the next birthday, today's if that is still ahead. */
    private fun nextMorning(now: LocalDateTime): LocalDateTime {
        val thisYear = LocalDate.of(now.year, MONTH, DAY)
        val day = if (now.toLocalDate().isAfter(thisYear)) thisYear.plusYears(1) else thisYear
        return day.atTime(9, 0)
    }

    /** Queues the morning greeting. Called on every app start; KEEP leaves an existing one alone. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        val target = nextMorning(now)
        val delay = Duration.between(now, target).toMillis().coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "birthday-${target.year}",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BirthdayWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences("birthday", Context.MODE_PRIVATE)

    /** The party ran; the morning notification is not needed any more this year. */
    fun markSeen(context: Context) {
        if (!isRealBirthday()) return
        prefs(context).edit().putBoolean("seen-${LocalDate.now().year}", true).apply()
    }

    /** Posts the greeting once per birthday, unless Helli already opened the app. */
    fun greet(context: Context) {
        if (!isRealBirthday()) return
        val year = LocalDate.now().year
        val p = prefs(context)
        if (p.getBoolean("seen-$year", false) || p.getBoolean("notified-$year", false)) return
        // Created only today so the channel does not show up in the system settings beforehand.
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Grüße", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Glückwünsche zum Geburtstag"
            },
        )
        Work.notify(
            context, NOTIFICATION_ID, CHANNEL,
            "Alles Gute zum ${age()}. Geburtstag, $NAME! 🎂",
            "$FROM hat dir in Kontor was versteckt. Keine Million, aber fast so gut. Tippe zum Öffnen.",
        )
        p.edit().putBoolean("notified-$year", true).apply()
    }
}

class BirthdayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Birthday.greet(applicationContext)
        return Result.success()
    }
}
