package app.pushcraft.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

const val DEFAULT_SENDER_ID = "pushcraft"
const val MAX_ACTIONS = 3

enum class SenderSource { DEFAULT, CUSTOM, APP }

data class Sender(
    val id: String,
    val name: String,
    val iconPath: String? = null,
    val source: SenderSource = SenderSource.CUSTOM,
    val packageName: String? = null,
    val accent: Int = 0xFF5B5BF6.toInt(),
) {
    companion object {
        val Default = Sender(DEFAULT_SENDER_ID, "Pushcraft", source = SenderSource.DEFAULT)
    }
}

enum class ActionKind(val label: String, val defaultLabel: String) {
    OPEN_URL("Open link", "Open"),
    OPEN_APP("Open app", "Open app"),
    REPLY("Reply", "Reply"),
    SNOOZE("Snooze", "Snooze"),
    DISMISS("Dismiss", "Dismiss"),
    SCRIPT("Run script", "Run"),
}

data class NotifAction(
    val id: String = newId(),
    val label: String = "",
    val kind: ActionKind = ActionKind.OPEN_URL,
    val target: String = "",
    val script: String = "",
    val withInput: Boolean = false,
    val snoozeMinutes: Int = 10,
    val placeholder: String = "Reply…",
    val destructive: Boolean = false,
) {
    val hasInput: Boolean get() = kind == ActionKind.REPLY || (kind == ActionKind.SCRIPT && withInput)

    val opensUi: Boolean
        get() = when (kind) {
            ActionKind.OPEN_URL, ActionKind.OPEN_APP -> true
            ActionKind.SCRIPT -> OPENS_UI.containsMatchIn(script)
            else -> false
        }

    companion object {
        private val OPENS_UI = Regex("""\bopen(Url|App)\s*\(""")
    }
}

enum class NotifStyle(val label: String) {
    STANDARD("Standard"), BIG_TEXT("Big text"), BIG_PICTURE("Image"), MESSAGING("Chat")
}

enum class Importance(val label: String) { BANNER("Banner"), QUIET("Quiet") }

data class Template(
    val id: String = newId(),
    val senderId: String = DEFAULT_SENDER_ID,
    val title: String = "",
    val body: String = "",
    val style: NotifStyle = NotifStyle.BIG_TEXT,
    val imagePath: String? = null,
    val actions: List<NotifAction> = emptyList(),
    val importance: Importance = Importance.BANNER,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class Repeat(val label: String) {
    NONE("Once"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly"), INTERVAL("Every…")
}

data class Schedule(
    val id: String = newId(),
    val templateId: String,
    val firstAt: Long,
    val repeat: Repeat = Repeat.NONE,
    val intervalMinutes: Int = 60,
    val enabled: Boolean = true,
    val nextAt: Long? = firstAt,
    val firedCount: Int = 0,
)

enum class LogKind { SENT, TAP, REPLY, SCRIPT, ERROR }

data class LogEntry(val at: Long, val kind: LogKind, val templateId: String?, val message: String)

const val SPAM_MAX = 100

enum class SpamMode(val label: String) { REPEAT("Same message"), CYCLE("Cycle messages") }

data class SpamConfig(
    val count: Int = 10,
    val intervalMs: Long = 800,
    val mode: SpamMode = SpamMode.REPEAT,
    val variants: List<String> = emptyList(),
)

data class SeqStep(
    val id: String = newId(),
    val senderId: String = DEFAULT_SENDER_ID,
    val title: String = "",
    val body: String = "",
    val style: NotifStyle = NotifStyle.MESSAGING,
    val importance: Importance = Importance.BANNER,
    val delayMs: Long = 1500,
)

data class Sequence(
    val id: String = newId(),
    val name: String = "",
    val steps: List<SeqStep> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class AppData(
    val senders: List<Sender> = emptyList(),
    val templates: List<Template> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val sequences: List<Sequence> = emptyList(),
    val spam: SpamConfig = SpamConfig(),
    val log: List<LogEntry> = emptyList(),
    val vars: Map<String, Map<String, Any?>> = emptyMap(),
    val counters: Map<String, Int> = emptyMap(),
    val nextNotifId: Int = 1000,
    val themeMode: Int = 0,
    val dynamicColor: Boolean = false,
    val seeded: Boolean = false,
)
