package app.pushcraft.script

data class ScriptExample(val title: String, val description: String, val code: String)

object ScriptExamples {
    val SMART_REPLY = ScriptExample(
        "Smart reply",
        "Branch on what the user typed (turn on the text box).",
        """
        // event.input holds the reply text when the button has a text box.
        const text = (event.input ?? '').toLowerCase()

        if (text.includes('late')) {
          notify({ title: 'No rush', body: 'We saved you a seat.' })
        } else if (text) {
          notify({ title: 'Got it', body: `You said: ${'$'}{event.input}` })
        } else {
          snooze(10)
        }
        """.trimIndent(),
    )

    val PATIENT_SNOOZE = ScriptExample(
        "Patient snooze",
        "Snooze longer each time, then give up after 3.",
        """
        const n = vars.inc('snoozes')

        if (n >= 3) {
          notify({ title: 'Okay, okay', body: `Snoozed ${'$'}{n} times. I give up.` })
          vars.set('snoozes', 0)
        } else {
          snooze(5 * n)
          log('snooze #' + n)
        }
        """.trimIndent(),
    )

    val STREAK = ScriptExample(
        "Streak counter",
        "Count taps and celebrate with a follow-up.",
        """
        const streak = vars.inc('streak')

        notify({
          title: `🔥 ${'$'}{streak} in a row`,
          body: pick(['Keep it going!', 'Nice work.', 'Unstoppable.'])
        })
        """.trimIndent(),
    )

    val TIME_AWARE_LINK = ScriptExample(
        "Time-aware link",
        "Open a different link depending on the time of day.",
        """
        // Mornings open the menu, everything else opens maps.
        if (event.hour < 12) {
          openUrl('https://example.com/menu')
        } else {
          openUrl('geo:0,0?q=coffee')
        }
        """.trimIndent(),
    )

    val WEEKEND = ScriptExample(
        "Weekend mode",
        "Different behavior on Saturday and Sunday.",
        """
        const weekend = event.weekday === 0 || event.weekday === 6

        if (weekend) {
          notify({ title: 'It is the weekend', body: 'Reminder muted until Monday.' })
        } else {
          snooze(30)
        }
        """.trimIndent(),
    )

    val all = listOf(SMART_REPLY, PATIENT_SNOOZE, STREAK, TIME_AWARE_LINK, WEEKEND)

    const val API_REFERENCE = """event.input      reply text or null
event.tapCount   times this button was tapped
event.action     button label
event.title / event.body
event.hour, event.minute, event.weekday (0 = Sun)

notify({ title, body })   post a follow-up
openUrl(url)              open a link / deep link
openApp(packageName)      launch an installed app
snooze(minutes)           show this again later
vars.get(k, fallback)     saved per template
vars.set(k, v)  vars.inc(k)  vars.has(k)
pick([a, b, c])           random item
log(...)                  shows in the simulator

Limits: 500 ms, 200k steps, 10 effects.
No network, files or device access."""
}
