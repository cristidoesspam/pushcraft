package app.pushcraft.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

object TemplateVars {
    private val TOKEN = Regex("""\{\{\s*([a-zA-Z]+)(?::([^}]*))?\s*\}\}""")

    val chips = listOf("{{time}}", "{{date}}", "{{weekday}}", "{{count}}", "{{pick:a|b|c}}", "{{var:name}}")

    fun render(text: String, at: Long, vars: Map<String, Any?>, count: Int, preview: Boolean = false): String {
        if (!text.contains("{{")) return text
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        return TOKEN.replace(text) { m ->
            val name = m.groupValues[1]
            val arg = m.groupValues[2]
            when (name.lowercase()) {
                "time" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time)
                "date" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(time)
                "weekday" -> time.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                "count" -> count.toString()
                "pick" -> {
                    val options = arg.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                    (if (preview) options.firstOrNull() else options.randomOrNull()) ?: ""
                }
                "var" -> vars[arg.trim()]?.let(::displayValue) ?: ""
                else -> m.value
            }
        }
    }

    fun displayValue(v: Any?): String = when (v) {
        null -> "null"
        is Double -> if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        else -> v.toString()
    }
}
