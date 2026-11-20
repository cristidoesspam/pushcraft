package app.pushcraft.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

class Repository private constructor(private val ctx: Context) {
    private val file = File(ctx.filesDir, "pushcraft.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppData> = _state
    val data: AppData get() = _state.value

    init {
        if (!data.seeded) update { Seed.starterData(ctx, it) }
    }

    @Synchronized
    fun update(fn: (AppData) -> AppData): AppData {
        val next = fn(_state.value)
        _state.value = next
        save(next)
        return next
    }

    private fun load(): AppData = try {
        if (file.exists()) appDataFromJson(JSONObject(file.readText())) else AppData()
    } catch (e: Exception) {
        Log.e("Pushcraft", "Could not read data, starting fresh", e)
        runCatching { file.copyTo(File(ctx.filesDir, "pushcraft.corrupt.json"), overwrite = true) }
        AppData()
    }

    private fun save(d: AppData) {
        val tmp = File(ctx.filesDir, "pushcraft.json.tmp")
        tmp.writeText(d.toJson().toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun template(id: String): Template? = data.templates.find { it.id == id }

    fun sender(id: String): Sender =
        data.senders.find { it.id == id } ?: Sender.Default

    fun vars(templateId: String): Map<String, Any?> = data.vars[templateId] ?: emptyMap()

    fun counter(key: String): Int = data.counters[key] ?: 0

    fun upsertTemplate(t: Template) = update { d ->
        d.copy(templates = listOf(t) + d.templates.filterNot { it.id == t.id })
    }

    fun deleteTemplate(id: String) = update { d ->
        d.copy(
            templates = d.templates.filterNot { it.id == id },
            schedules = d.schedules.filterNot { it.templateId == id },
            vars = d.vars - id,
        )
    }

    fun upsertSender(s: Sender) = update { d ->
        d.copy(senders = listOf(s) + d.senders.filterNot { it.id == s.id })
    }

    fun deleteSender(id: String) = update { d ->
        d.copy(
            senders = d.senders.filterNot { it.id == id },
            templates = d.templates.map { if (it.senderId == id) it.copy(senderId = DEFAULT_SENDER_ID) else it },
        )
    }

    fun upsertSchedule(s: Schedule) = update { d ->
        val i = d.schedules.indexOfFirst { it.id == s.id }
        d.copy(schedules = if (i < 0) d.schedules + s else d.schedules.toMutableList().also { it[i] = s })
    }

    fun deleteSchedule(id: String) = update { d -> d.copy(schedules = d.schedules.filterNot { it.id == id }) }

    fun sequence(id: String): Sequence? = data.sequences.find { it.id == id }

    fun upsertSequence(s: Sequence) = update { d ->
        d.copy(sequences = listOf(s) + d.sequences.filterNot { it.id == s.id })
    }

    fun deleteSequence(id: String) = update { d -> d.copy(sequences = d.sequences.filterNot { it.id == id }) }

    fun setSpam(config: SpamConfig) = update { it.copy(spam = config) }

    fun setVars(templateId: String, vars: Map<String, Any?>) = update { d ->
        d.copy(vars = d.vars + (templateId to vars))
    }

    fun increment(key: String): Int {
        var value = 0
        update { d ->
            value = (d.counters[key] ?: 0) + 1
            d.copy(counters = d.counters + (key to value))
        }
        return value
    }

    fun nextNotifId(): Int {
        var id = 0
        update { d ->
            id = d.nextNotifId
            d.copy(nextNotifId = if (id > 1_000_000_000) 1000 else id + 1)
        }
        return id
    }

    fun log(kind: LogKind, templateId: String?, message: String) = update { d ->
        d.copy(log = (listOf(LogEntry(System.currentTimeMillis(), kind, templateId, message)) + d.log).take(200))
    }

    fun clearLog() = update { it.copy(log = emptyList()) }

    fun setThemeMode(mode: Int) = update { it.copy(themeMode = mode) }

    fun setDynamicColor(on: Boolean) = update { it.copy(dynamicColor = on) }

    companion object {
        @Volatile private var instance: Repository? = null

        fun get(ctx: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(ctx.applicationContext).also { instance = it }
            }
    }
}
