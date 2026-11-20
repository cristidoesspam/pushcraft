package app.pushcraft.script

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

sealed class Effect {
    data class Notify(val title: String, val body: String) : Effect()
    data class OpenUrl(val url: String) : Effect()
    data class OpenApp(val pkg: String) : Effect()
    data class Snooze(val minutes: Int) : Effect()
}

data class ScriptEvent(
    val input: String?,
    val tapCount: Int,
    val action: String,
    val title: String,
    val body: String,
    val firedAt: Long = System.currentTimeMillis(),
)

data class ScriptResult(
    val effects: List<Effect>,
    val logs: List<String>,
    val vars: Map<String, Any?>,
    val error: ScriptError?,
    val millis: Long,
)

object ScriptHost {
    private val BLOCKED_SCHEMES = setOf("file", "content", "intent", "javascript", "data", "android-app")
    private const val MAX_EFFECTS = 10

    fun normalizeUrl(raw: String): String {
        val s = raw.trim()
        return if (Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*:""").containsMatchIn(s)) s else "https://$s"
    }

    fun isAllowedUrl(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        return scheme.isNotEmpty() && scheme !in BLOCKED_SCHEMES
    }

    fun run(source: String, event: ScriptEvent, vars: Map<String, Any?>): ScriptResult {
        val start = System.nanoTime()
        val effects = mutableListOf<Effect>()
        val logs = mutableListOf<String>()
        val store = LinkedHashMap(vars)
        var error: ScriptError? = null

        fun add(e: Effect) {
            if (effects.size >= MAX_EFFECTS) throw ScriptError("Too many effects (max $MAX_EFFECTS)")
            effects += e
        }
        fun primitive(v: Any?): Any? = when (v) {
            null, is String, is Double, is Boolean -> v
            Undefined -> null
            else -> Interpreter.display(v, quoteStrings = true)
        }

        try {
            val program = Parser.parse(source)
            val interp = Interpreter()
            val g = interp.globals
            fun def(name: String, impl: (List<Any?>) -> Any?) = g.declare(name, NativeFn(name, impl), true, 0)
            fun arg(a: List<Any?>, i: Int) = a.getOrElse(i) { Undefined }

            def("notify") { a ->
                val first = arg(a, 0)
                val (title, body) = if (first is JsObject) {
                    (first.props["title"] ?: "") to (first.props["body"] ?: "")
                } else first to arg(a, 1)
                val t = if (Interpreter.isNullish(title)) "" else Interpreter.display(title)
                val b = if (Interpreter.isNullish(body)) "" else Interpreter.display(body)
                add(Effect.Notify(t.take(120), b.take(1000)))
                Undefined
            }
            def("openUrl") { a ->
                val url = normalizeUrl(Interpreter.toStr(arg(a, 0)))
                if (!isAllowedUrl(url)) throw ScriptError("openUrl: '${url.substringBefore(':')}:' links are blocked")
                add(Effect.OpenUrl(url))
                Undefined
            }
            def("openApp") { a -> add(Effect.OpenApp(Interpreter.toStr(arg(a, 0)).trim())); Undefined }
            def("snooze") { a ->
                val n = Interpreter.toNum(arg(a, 0)).let { if (it.isNaN()) 10.0 else it }
                add(Effect.Snooze(n.toInt().coerceIn(1, 1440)))
                Undefined
            }
            def("log") { a -> logs += a.joinToString(" ") { Interpreter.display(it) }; Undefined }
            def("pick") { a ->
                val list = (arg(a, 0) as? JsArray)?.items ?: a
                if (list.isEmpty()) Undefined else list[Random.nextInt(list.size)]
            }
            g.declare("console", JsObject(linkedMapOf<String, Any?>(
                "log" to NativeFn("log") { a -> logs += a.joinToString(" ") { Interpreter.display(it) }; Undefined },
            )), true, 0)
            g.declare("vars", JsObject(linkedMapOf<String, Any?>(
                "get" to NativeFn("get") { a ->
                    val k = Interpreter.toStr(arg(a, 0))
                    if (store.containsKey(k)) store[k] else arg(a, 1)
                },
                "set" to NativeFn("set") { a ->
                    if (store.size >= 100 && !store.containsKey(Interpreter.toStr(arg(a, 0)))) throw ScriptError("Too many vars (max 100)")
                    store[Interpreter.toStr(arg(a, 0))] = primitive(arg(a, 1)); Undefined
                },
                "inc" to NativeFn("inc") { a ->
                    val k = Interpreter.toStr(arg(a, 0))
                    val by = arg(a, 1).let { if (it === Undefined) 1.0 else Interpreter.toNum(it) }
                    val next = Interpreter.toNum(store[k] ?: 0.0).let { if (it.isNaN()) 0.0 else it } + by
                    store[k] = next
                    next
                },
                "has" to NativeFn("has") { a -> store.containsKey(Interpreter.toStr(arg(a, 0))) },
                "remove" to NativeFn("remove") { a -> store.remove(Interpreter.toStr(arg(a, 0))); Undefined },
            )), true, 0)
            val time = Instant.ofEpochMilli(event.firedAt).atZone(ZoneId.systemDefault())
            g.declare("event", JsObject(linkedMapOf<String, Any?>(
                "input" to event.input,
                "tapCount" to event.tapCount.toDouble(),
                "action" to event.action,
                "title" to event.title,
                "body" to event.body,
                "hour" to time.hour.toDouble(),
                "minute" to time.minute.toDouble(),
                "weekday" to (time.dayOfWeek.value % 7).toDouble(),
                "firedAt" to event.firedAt.toDouble(),
            )), true, 0)

            interp.run(program)
        } catch (e: ScriptError) {
            error = e
        } catch (e: StackOverflowError) {
            error = ScriptError("Too much recursion")
        } catch (e: Exception) {
            error = ScriptError("Internal error: ${e.message ?: e.javaClass.simpleName}")
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        return if (error != null) ScriptResult(emptyList(), logs, vars, error, ms)
        else ScriptResult(effects, logs, store, null, ms)
    }
}
