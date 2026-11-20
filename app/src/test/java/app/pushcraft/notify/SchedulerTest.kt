package app.pushcraft.notify

import app.pushcraft.data.Repeat
import app.pushcraft.data.Schedule
import app.pushcraft.data.TemplateVars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class SchedulerTest {
    private val zone = ZoneId.systemDefault()
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    private fun dt(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    private val wed9 = LocalDateTime.of(2026, 10, 7, 9, 0)

    @Test fun onceInFutureThenDone() {
        val s = Schedule(templateId = "t", firstAt = ms(wed9))
        assertEquals(ms(wed9), Scheduler.computeNext(s, ms(wed9.minusHours(1))))
        assertNull(Scheduler.computeNext(s, ms(wed9)))
    }

    @Test fun daily() {
        val s = Schedule(templateId = "t", firstAt = ms(wed9), repeat = Repeat.DAILY)
        assertEquals(wed9, dt(Scheduler.computeNext(s, ms(wed9.minusDays(3)))!!))
        assertEquals(wed9.plusDays(1), dt(Scheduler.computeNext(s, ms(wed9.plusMinutes(1)))!!))
    }

    @Test fun weekdaysSkipWeekend() {
        val s = Schedule(templateId = "t", firstAt = ms(wed9), repeat = Repeat.WEEKDAYS)
        val friAfter = wed9.plusDays(2).plusHours(1)
        val next = dt(Scheduler.computeNext(s, ms(friAfter))!!)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(9, next.hour)
    }

    @Test fun weeklySameWeekday() {
        val s = Schedule(templateId = "t", firstAt = ms(wed9), repeat = Repeat.WEEKLY)
        assertEquals(wed9.plusWeeks(1), dt(Scheduler.computeNext(s, ms(wed9.plusHours(1)))!!))
    }

    @Test fun monthlyClampsShortMonths() {
        val jan31 = LocalDateTime.of(2027, 1, 31, 8, 0)
        val s = Schedule(templateId = "t", firstAt = ms(jan31), repeat = Repeat.MONTHLY)
        assertEquals(LocalDateTime.of(2027, 2, 28, 8, 0), dt(Scheduler.computeNext(s, ms(jan31.plusHours(1)))!!))
    }

    @Test fun interval() {
        val s = Schedule(templateId = "t", firstAt = ms(wed9), repeat = Repeat.INTERVAL, intervalMinutes = 30)
        assertEquals(wed9.plusMinutes(90), dt(Scheduler.computeNext(s, ms(wed9.plusMinutes(61)))!!))
    }

    @Test fun templateVars() {
        val at = ms(wed9)
        assertEquals("Glass #3 · a · 7", TemplateVars.render("Glass #{{count}} · {{pick:a|b}} · {{var:n}}", at, mapOf("n" to 7.0), 3, preview = true))
        assertEquals("Wednesday", TemplateVars.render("{{weekday}}", at, emptyMap(), 0).let {
            java.time.DayOfWeek.WEDNESDAY.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()).let { w -> if (it == w) "Wednesday" else it }
        })
    }
}
