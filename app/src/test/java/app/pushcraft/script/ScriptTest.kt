package app.pushcraft.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScriptTest {
    private fun event(input: String? = null, taps: Int = 1, hour: Int = 9, date: LocalDate = LocalDate.of(2026, 10, 7)) = ScriptEvent(
        input = input, tapCount = taps, action = "Go", title = "T", body = "B",
        firedAt = date.atTime(LocalTime.of(hour, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    private fun run(src: String, e: ScriptEvent = event(), vars: Map<String, Any?> = emptyMap()) = ScriptHost.run(src, e, vars)

    private fun ok(src: String, e: ScriptEvent = event(), vars: Map<String, Any?> = emptyMap()): ScriptResult {
        val r = run(src, e, vars)
        assertNull("unexpected error: ${r.error?.message} (line ${r.error?.line})", r.error)
        return r
    }

    private fun logOf(src: String) = ok(src).logs.joinToString("|")

    @Test fun smartReply() {
        val code = ScriptExamples.SMART_REPLY.code
        assertEquals(listOf(Effect.Notify("No rush", "We saved you a seat.")), ok(code, event("Running LATE")).effects)
        assertEquals(listOf(Effect.Notify("Got it", "You said: hello")), ok(code, event("hello")).effects)
        assertEquals(listOf(Effect.Snooze(10)), ok(code, event(null)).effects)
    }

    @Test fun patientSnooze() {
        val code = ScriptExamples.PATIENT_SNOOZE.code
        val first = ok(code)
        assertEquals(listOf(Effect.Snooze(5)), first.effects)
        assertEquals(listOf("snooze #1"), first.logs)
        assertEquals(1.0, first.vars["snoozes"])
        val third = ok(code, vars = mapOf("snoozes" to 2.0))
        assertEquals(listOf(Effect.Notify("Okay, okay", "Snoozed 3 times. I give up.")), third.effects)
        assertEquals(0.0, third.vars["snoozes"])
    }

    @Test fun streak() {
        val r = ok(ScriptExamples.STREAK.code, vars = mapOf("streak" to 4.0))
        val n = r.effects.single() as Effect.Notify
        assertEquals("🔥 5 in a row", n.title)
        assertTrue(n.body in listOf("Keep it going!", "Nice work.", "Unstoppable."))
    }

    @Test fun timeAwareLink() {
        assertEquals(listOf(Effect.OpenUrl("https://example.com/menu")), ok(ScriptExamples.TIME_AWARE_LINK.code, event(hour = 9)).effects)
        assertEquals(listOf(Effect.OpenUrl("geo:0,0?q=coffee")), ok(ScriptExamples.TIME_AWARE_LINK.code, event(hour = 15)).effects)
    }

    @Test fun weekend() {
        val saturday = LocalDate.of(2026, 10, 10)
        assertTrue(ok(ScriptExamples.WEEKEND.code, event(date = saturday)).effects.single() is Effect.Notify)
        assertEquals(listOf(Effect.Snooze(30)), ok(ScriptExamples.WEEKEND.code, event(date = saturday.plusDays(2))).effects)
    }

    @Test fun allExamplesParse() {
        ScriptExamples.all.forEach { Parser.parse(it.code) }
    }

    @Test fun numbersPrintLikeJs() {
        assertEquals("3|0.5|-2|NaN|10", logOf("log(1 + 2); log(1 / 2); log(-2); log(0 / 0); log(2 * 5)"))
        assertEquals("1.50", logOf("log((1.5).toFixed(2))"))
    }

    @Test fun stringsAndTemplates() {
        assertEquals("a1|HI|x-y-z|3|ab", logOf("""
            log('a' + 1)
            log("hi".toUpperCase())
            log(['x','y','z'].join('-'))
            log(`${'$'}{1 + 2}`)
            log('abc'.slice(0, -1))
        """.trimIndent()))
    }

    @Test fun functionsClosuresAndArrays() {
        assertEquals("120|[2,4,6]|[3]|true|6", logOf("""
            function fact(n) { return n <= 1 ? 1 : n * fact(n - 1) }
            log(fact(5))
            log(JSON.stringify([1, 2, 3].map(x => x * 2)))
            log(JSON.stringify([1, 2, 3].filter((x) => x > 2)))
            const make = () => { let c = 0; return () => ++c }
            const inc = make(); inc(); inc()
            log(inc() === 3)
            let sum = 0
            for (const x of [1, 2, 3]) sum += x
            log(sum)
        """.trimIndent()))
    }

    @Test fun loopsBreakContinue() {
        assertEquals("0,1,3,4", logOf("""
            const out = []
            for (let i = 0; i < 10; i++) {
              if (i === 2) continue
              if (i === 5) break
              out.push(i)
            }
            log(out.join(','))
        """.trimIndent()))
    }

    @Test fun optionalChainingShortCircuits() {
        val r = ok("log(event.input?.toLowerCase().includes('x')); log(event.input ?? 'none')", event(null))
        assertEquals(listOf("undefined", "none"), r.logs)
    }

    @Test fun objectsAndTypeof() {
        assertEquals("number|string|undefined|object|2", logOf("""
            const o = { a: 1, 'b': 'x' }
            log(typeof o.a); log(typeof o.b); log(typeof nope); log(typeof null)
            o.c = 2; log(o.c)
        """.trimIndent()))
    }

    @Test fun smartQuotesAreAccepted() {
        assertEquals(listOf(Effect.OpenUrl("https://example.com")), ok("openUrl(‘https://example.com’)").effects)
    }

    @Test fun notifyPositionalForm() {
        assertEquals(listOf(Effect.Notify("Hi", "there")), ok("notify('Hi', 'there')").effects)
    }

    @Test fun undefinedVariableReportsLine() {
        val r = run("let a = 1\n\nlog(b)")
        assertEquals(3, r.error?.line)
    }

    @Test fun constCannotBeReassigned() {
        assertNotNull(run("const a = 1\na = 2").error)
    }

    @Test fun infiniteLoopIsStopped() {
        val r = run("while (true) {}")
        assertTrue(r.error?.message.orEmpty().contains("limit") || r.error?.message.orEmpty().contains("Timed out"))
    }

    @Test fun runawayRecursionIsStopped() {
        assertNotNull(run("function f() { return f() }\nf()").error)
    }

    @Test fun tooManyEffects() {
        val r = run("for (let i = 0; i < 20; i++) snooze(1)")
        assertNotNull(r.error)
        assertTrue(r.effects.isEmpty())
    }

    @Test fun blockedSchemes() {
        assertNotNull(run("openUrl('javascript:alert(1)')").error)
        assertNotNull(run("openUrl('file:///sdcard/x')").error)
        assertEquals(listOf(Effect.OpenUrl("https://example.com")), ok("openUrl('example.com')").effects)
    }

    @Test fun failedRunKeepsOldVars() {
        val r = run("vars.set('a', 5)\nthrowMe()", vars = mapOf("a" to 1.0))
        assertNotNull(r.error)
        assertEquals(1.0, r.vars["a"])
    }

    @Test fun syntaxErrorHasLine() {
        val r = run("if (x {\n}")
        assertNotNull(r.error)
        assertEquals(1, r.error?.line)
    }
}
