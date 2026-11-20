package app.pushcraft.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

private inline fun <reified E : Enum<E>> JSONObject.enum(k: String, default: E): E =
    str(k)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

private inline fun <T> JSONArray?.mapObjects(fn: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { fn(getJSONObject(it)) }

private fun jsonValue(v: Any?): Any = when (v) {
    null -> JSONObject.NULL
    is Double -> if (v.isNaN() || v.isInfinite()) v.toString() else v
    is Boolean, is String -> v
    else -> v.toString()
}

private fun fromJsonValue(v: Any?): Any? = when (v) {
    null, JSONObject.NULL -> null
    is Number -> v.toDouble()
    is Boolean, is String -> v
    else -> v.toString()
}

fun Sender.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("iconPath", iconPath)
    .put("source", source.name).put("packageName", packageName).put("accent", accent)

fun senderFromJson(o: JSONObject) = Sender(
    id = o.getString("id"),
    name = o.optString("name"),
    iconPath = o.str("iconPath"),
    source = o.enum("source", SenderSource.CUSTOM),
    packageName = o.str("packageName"),
    accent = o.optInt("accent", 0xFF5B5BF6.toInt()),
)

fun NotifAction.toJson(): JSONObject = JSONObject()
    .put("id", id).put("label", label).put("kind", kind.name).put("target", target)
    .put("script", script).put("withInput", withInput).put("snoozeMinutes", snoozeMinutes)
    .put("placeholder", placeholder).put("destructive", destructive)

fun actionFromJson(o: JSONObject) = NotifAction(
    id = o.getString("id"),
    label = o.optString("label"),
    kind = o.enum("kind", ActionKind.DISMISS),
    target = o.optString("target"),
    script = o.optString("script"),
    withInput = o.optBoolean("withInput"),
    snoozeMinutes = o.optInt("snoozeMinutes", 10),
    placeholder = o.optString("placeholder", "Reply…"),
    destructive = o.optBoolean("destructive"),
)

fun Template.toJson(): JSONObject = JSONObject()
    .put("id", id).put("senderId", senderId).put("title", title).put("body", body)
    .put("style", style.name).put("imagePath", imagePath)
    .put("actions", JSONArray(actions.map { it.toJson() }))
    .put("importance", importance.name).put("updatedAt", updatedAt)

fun templateFromJson(o: JSONObject) = Template(
    id = o.getString("id"),
    senderId = o.optString("senderId", DEFAULT_SENDER_ID),
    title = o.optString("title"),
    body = o.optString("body"),
    style = o.enum("style", NotifStyle.BIG_TEXT),
    imagePath = o.str("imagePath"),
    actions = o.optJSONArray("actions").mapObjects(::actionFromJson),
    importance = o.enum("importance", Importance.BANNER),
    updatedAt = o.optLong("updatedAt"),
)

fun Schedule.toJson(): JSONObject = JSONObject()
    .put("id", id).put("templateId", templateId).put("firstAt", firstAt)
    .put("repeat", repeat.name).put("intervalMinutes", intervalMinutes).put("enabled", enabled)
    .put("nextAt", nextAt).put("firedCount", firedCount)

fun scheduleFromJson(o: JSONObject) = Schedule(
    id = o.getString("id"),
    templateId = o.getString("templateId"),
    firstAt = o.getLong("firstAt"),
    repeat = o.enum("repeat", Repeat.NONE),
    intervalMinutes = o.optInt("intervalMinutes", 60),
    enabled = o.optBoolean("enabled", true),
    nextAt = if (o.has("nextAt") && !o.isNull("nextAt")) o.getLong("nextAt") else null,
    firedCount = o.optInt("firedCount"),
)

fun SeqStep.toJson(): JSONObject = JSONObject()
    .put("id", id).put("senderId", senderId).put("title", title).put("body", body)
    .put("style", style.name).put("importance", importance.name).put("delayMs", delayMs)

fun seqStepFromJson(o: JSONObject) = SeqStep(
    id = o.getString("id"),
    senderId = o.optString("senderId", DEFAULT_SENDER_ID),
    title = o.optString("title"),
    body = o.optString("body"),
    style = o.enum("style", NotifStyle.MESSAGING),
    importance = o.enum("importance", Importance.BANNER),
    delayMs = o.optLong("delayMs", 1500),
)

fun Sequence.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("updatedAt", updatedAt)
    .put("steps", JSONArray(steps.map { it.toJson() }))

fun sequenceFromJson(o: JSONObject) = Sequence(
    id = o.getString("id"),
    name = o.optString("name"),
    steps = o.optJSONArray("steps").mapObjects(::seqStepFromJson),
    updatedAt = o.optLong("updatedAt"),
)

fun SpamConfig.toJson(): JSONObject = JSONObject()
    .put("count", count).put("intervalMs", intervalMs).put("mode", mode.name)
    .put("variants", JSONArray(variants))

fun spamFromJson(o: JSONObject): SpamConfig {
    val variants = o.optJSONArray("variants")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
    return SpamConfig(
        count = o.optInt("count", 10),
        intervalMs = o.optLong("intervalMs", 800),
        mode = o.enum("mode", SpamMode.REPEAT),
        variants = variants,
    )
}

fun LogEntry.toJson(): JSONObject = JSONObject()
    .put("at", at).put("kind", kind.name).put("templateId", templateId).put("message", message)

fun logFromJson(o: JSONObject) = LogEntry(
    at = o.getLong("at"),
    kind = o.enum("kind", LogKind.SENT),
    templateId = o.str("templateId"),
    message = o.optString("message"),
)

fun AppData.toJson(): JSONObject {
    val varsJson = JSONObject()
    vars.forEach { (tpl, map) ->
        varsJson.put(tpl, JSONObject().apply { map.forEach { (k, v) -> put(k, jsonValue(v)) } })
    }
    return JSONObject()
        .put("version", 1)
        .put("senders", JSONArray(senders.map { it.toJson() }))
        .put("templates", JSONArray(templates.map { it.toJson() }))
        .put("schedules", JSONArray(schedules.map { it.toJson() }))
        .put("sequences", JSONArray(sequences.map { it.toJson() }))
        .put("spam", spam.toJson())
        .put("log", JSONArray(log.map { it.toJson() }))
        .put("vars", varsJson)
        .put("counters", JSONObject(counters as Map<*, *>))
        .put("nextNotifId", nextNotifId)
        .put("themeMode", themeMode)
        .put("dynamicColor", dynamicColor)
        .put("seeded", seeded)
}

fun appDataFromJson(o: JSONObject): AppData {
    val vars = mutableMapOf<String, Map<String, Any?>>()
    o.optJSONObject("vars")?.let { all ->
        all.keys().forEach { tpl ->
            val m = all.getJSONObject(tpl)
            vars[tpl] = m.keys().asSequence().associateWith { fromJsonValue(m.opt(it)) }
        }
    }
    val counters = mutableMapOf<String, Int>()
    o.optJSONObject("counters")?.let { c -> c.keys().forEach { counters[it] = c.optInt(it) } }
    return AppData(
        senders = o.optJSONArray("senders").mapObjects(::senderFromJson),
        templates = o.optJSONArray("templates").mapObjects(::templateFromJson),
        schedules = o.optJSONArray("schedules").mapObjects(::scheduleFromJson),
        sequences = o.optJSONArray("sequences").mapObjects(::sequenceFromJson),
        spam = o.optJSONObject("spam")?.let(::spamFromJson) ?: SpamConfig(),
        log = o.optJSONArray("log").mapObjects(::logFromJson),
        vars = vars,
        counters = counters,
        nextNotifId = o.optInt("nextNotifId", 1000),
        themeMode = o.optInt("themeMode", 0),
        dynamicColor = o.optBoolean("dynamicColor", false),
        seeded = o.optBoolean("seeded", false),
    )
}
