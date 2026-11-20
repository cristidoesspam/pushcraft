package app.pushcraft.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.pushcraft.data.Repeat
import app.pushcraft.data.Repository
import app.pushcraft.data.Schedule
import app.pushcraft.data.newId
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

const val ACTION_FIRE_SCHEDULE = "app.pushcraft.FIRE_SCHEDULE"
const val ACTION_FIRE_ONESHOT = "app.pushcraft.FIRE_ONESHOT"

object Scheduler {
    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    fun computeNext(s: Schedule, after: Long): Long? {
        val zone = ZoneId.systemDefault()
        val first = Instant.ofEpochMilli(s.firstAt).atZone(zone)
        return when (s.repeat) {
            Repeat.NONE -> if (s.firstAt > after) s.firstAt else null
            Repeat.INTERVAL -> {
                if (s.firstAt > after) return s.firstAt
                val step = s.intervalMinutes.coerceAtLeast(1) * 60_000L
                s.firstAt + ((after - s.firstAt) / step + 1) * step
            }
            Repeat.DAILY -> nextMatching(first, maxOf(after, s.firstAt - 1)) { true }
            Repeat.WEEKDAYS -> nextMatching(first, maxOf(after, s.firstAt - 1)) {
                it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY
            }
            Repeat.WEEKLY -> nextMatching(first, maxOf(after, s.firstAt - 1)) { it.dayOfWeek == first.dayOfWeek }
            Repeat.MONTHLY -> {
                if (s.firstAt > after) return s.firstAt
                generateSequence(1L) { it + 1 }
                    .map { first.plusMonths(it).toInstant().toEpochMilli() }
                    .first { it > after }
            }
        }
    }

    private fun nextMatching(first: ZonedDateTime, after: Long, ok: (ZonedDateTime) -> Boolean): Long {
        var date = Instant.ofEpochMilli(after).atZone(first.zone).toLocalDate()
        repeat(15) {
            val candidate = date.atTime(first.toLocalTime()).atZone(first.zone)
            if (candidate.toInstant().toEpochMilli() > after && ok(candidate)) return candidate.toInstant().toEpochMilli()
            date = date.plusDays(1)
        }
        error("no occurrence found")
    }

    private fun schedulePi(ctx: Context, id: String): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE_SCHEDULE)
            .setData(Uri.parse("pushcraft://schedule/$id"))
            .putExtra(EXTRA_SCHEDULE, id)
        return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = am(ctx)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun arm(ctx: Context, s: Schedule) {
        val pi = schedulePi(ctx, s.id)
        am(ctx).cancel(pi)
        val at = s.nextAt ?: return
        if (s.enabled) setAlarm(ctx, at, pi)
    }

    fun cancel(ctx: Context, scheduleId: String) = am(ctx).cancel(schedulePi(ctx, scheduleId))

    fun save(ctx: Context, s: Schedule): Schedule {
        val next = s.copy(nextAt = if (s.enabled) computeNext(s, System.currentTimeMillis()) else s.nextAt)
        Repository.get(ctx).upsertSchedule(next)
        arm(ctx, next)
        return next
    }

    fun setEnabled(ctx: Context, s: Schedule, enabled: Boolean) {
        save(ctx, s.copy(enabled = enabled))
    }

    fun delete(ctx: Context, id: String) {
        cancel(ctx, id)
        Repository.get(ctx).deleteSchedule(id)
    }

    fun fire(ctx: Context, id: String) {
        val repo = Repository.get(ctx)
        val s = repo.data.schedules.find { it.id == id } ?: return
        val t = repo.template(s.templateId)
        if (s.enabled && t != null) Poster.post(ctx, t)
        val next = computeNext(s, System.currentTimeMillis() + 1_000)
        val updated = s.copy(nextAt = next, firedCount = s.firedCount + 1)
        repo.upsertSchedule(updated)
        arm(ctx, updated)
    }

    fun rearmAll(ctx: Context) {
        val repo = Repository.get(ctx)
        val now = System.currentTimeMillis()
        val staleBefore = now - 12 * 60 * 60 * 1000L
        for (s in repo.data.schedules) {
            var current = s
            val next = s.nextAt
            if (s.enabled && next != null && next < staleBefore) {
                current = s.copy(nextAt = computeNext(s, now))
                repo.upsertSchedule(current)
            }
            arm(ctx, current)
        }
    }

    fun sendLater(ctx: Context, templateId: String, delayMs: Long) {
        val app = ctx.applicationContext
        if (!canExact(app) && delayMs <= 60_000) {
            Handler(Looper.getMainLooper()).postDelayed({
                Repository.get(app).template(templateId)?.let { Poster.post(app, it) }
            }, delayMs)
            return
        }
        val intent = Intent(app, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE_ONESHOT)
            .setData(Uri.parse("pushcraft://oneshot/${newId()}"))
            .putExtra(EXTRA_TEMPLATE, templateId)
        val pi = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setAlarm(app, System.currentTimeMillis() + delayMs, pi)
    }
}
