package app.pushcraft.script

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

object Undefined { override fun toString() = "undefined" }

class JsObject(val props: LinkedHashMap<String, Any?> = LinkedHashMap())
class JsArray(val items: MutableList<Any?> = mutableListOf())

sealed class JsCallable { abstract val name: String }
class JsFunction(val fn: FnE, val closure: Env) : JsCallable() {
    override val name: String get() = fn.name ?: "anonymous"
}
class NativeFn(override val name: String, val impl: (args: List<Any?>) -> Any?) : JsCallable()

class Env(private val parent: Env?) {
    class Binding(var value: Any?, val const: Boolean)
    private val vars = HashMap<String, Binding>()

    fun declare(name: String, value: Any?, const: Boolean, line: Int) {
        if (vars.containsKey(name)) throw ScriptError("'$name' is already declared", line)
        vars[name] = Binding(value, const)
    }
    fun declareVar(name: String, value: Any?) {
        val b = vars[name]
        if (b != null) b.value = value else vars[name] = Binding(value, false)
    }
    fun lookup(name: String): Binding? = vars[name] ?: parent?.lookup(name)
}

private class ReturnSignal(val value: Any?) : Throwable(null, null, false, false)
private object BreakSignal : Throwable(null, null, false, false) { private fun readResolve(): Any = BreakSignal }
private object ContinueSignal : Throwable(null, null, false, false) { private fun readResolve(): Any = ContinueSignal }
private object ShortCircuit : Throwable(null, null, false, false) { private fun readResolve(): Any = ShortCircuit }

class Interpreter(
    private val maxSteps: Int = 200_000,
    timeoutMs: Long = 500,
) {
    private var steps = 0
    private var depth = 0
    private val deadline = System.nanoTime() + timeoutMs * 1_000_000
    private val timeoutMs = timeoutMs
    val globals = Env(null)

    init { installBuiltins() }

    fun run(program: BlockS) {
        hoist(program.body, globals)
        try {
            for (s in program.body) exec(s, globals)
        } catch (e: ReturnSignal) {
        } catch (e: BreakSignal) {
            throw ScriptError("'break' outside a loop")
        } catch (e: ContinueSignal) {
            throw ScriptError("'continue' outside a loop")
        }
    }

    private fun tick(line: Int) {
        if (++steps > maxSteps) throw ScriptError("Step limit reached — infinite loop?", line)
        if (steps and 1023 == 0 && System.nanoTime() > deadline) throw ScriptError("Timed out after $timeoutMs ms", line)
    }

    private fun hoist(body: List<Node>, env: Env) {
        for (s in body) if (s is FnDeclS) env.declareVar(s.fn.name!!, JsFunction(s.fn, env))
    }

    private fun exec(n: Node, env: Env) {
        tick(n.line)
        when (n) {
            is ExprS -> eval(n.e, env)
            is VarS -> for ((name, init) in n.decls) {
                val v = if (init != null) eval(init, env) else Undefined
                if (n.kind == "var") env.declareVar(name, v) else env.declare(name, v, n.kind == "const", n.line)
            }
            is IfS -> if (truthy(eval(n.test, env))) exec(n.then, env) else n.otherwise?.let { exec(it, env) }
            is BlockS -> {
                val inner = Env(env)
                hoist(n.body, inner)
                for (s in n.body) exec(s, inner)
            }
            is WhileS -> while (truthy(eval(n.test, env))) {
                tick(n.line)
                try { exec(n.body, env) } catch (e: BreakSignal) { break } catch (e: ContinueSignal) { }
            }
            is ForS -> {
                val loopEnv = Env(env)
                n.init?.let { exec(it, loopEnv) }
                while (n.test == null || truthy(eval(n.test, loopEnv))) {
                    tick(n.line)
                    try { exec(n.body, loopEnv) } catch (e: BreakSignal) { break } catch (e: ContinueSignal) { }
                    n.update?.let { eval(it, loopEnv) }
                }
            }
            is ForOfS -> {
                val items: List<Any?> = when (val it = eval(n.iter, env)) {
                    is JsArray -> it.items.toList()
                    is String -> it.map { c -> c.toString() }
                    is JsObject -> it.props.keys.toList()
                    else -> throw ScriptError("${typeOf(it)} is not iterable", n.line)
                }
                for (item in items) {
                    tick(n.line)
                    val e = Env(env)
                    e.declare(n.name, item, n.kind == "const", n.line)
                    try { exec(n.body, e) } catch (e: BreakSignal) { break } catch (e: ContinueSignal) { }
                }
            }
            is ReturnS -> throw ReturnSignal(if (n.arg != null) eval(n.arg, env) else Undefined)
            is BreakS -> throw BreakSignal
            is ContinueS -> throw ContinueSignal
            is FnDeclS, is EmptyS -> Unit
            else -> eval(n, env)
        }
    }

    private fun eval(n: Node, env: Env): Any? = when (n) {
        is NumE -> n.v
        is StrE -> n.v
        is LitE -> n.v
        is TplE -> capString(buildString { for (part in n.parts) append(toStr(eval(part, env))) }, n.line)
        is IdentE -> (env.lookup(n.name) ?: throw ScriptError("'${n.name}' is not defined", n.line)).value
        is ObjE -> JsObject(LinkedHashMap<String, Any?>().apply { for ((k, v) in n.props) put(k, eval(v, env)) })
        is ArrE -> JsArray(n.items.mapTo(mutableListOf()) { eval(it, env) })
        is ChainE -> try { eval(n.e, env) } catch (e: ShortCircuit) { Undefined }
        is MemberE -> {
            val obj = eval(n.obj, env)
            if (n.optional && isNullish(obj)) throw ShortCircuit
            getProp(obj, propKey(n, env), n.line)
        }
        is CallE -> evalCall(n, env)
        is UnaryE -> when (n.op) {
            "!" -> !truthy(eval(n.arg, env))
            "-" -> -toNum(eval(n.arg, env))
            "+" -> toNum(eval(n.arg, env))
            else -> {
                val a = n.arg
                if (a is IdentE && env.lookup(a.name) == null) "undefined" else typeOf(eval(a, env))
            }
        }
        is BinE -> binary(n.op, eval(n.l, env), eval(n.r, env), n.line)
        is LogicE -> {
            val l = eval(n.l, env)
            when (n.op) {
                "&&" -> if (!truthy(l)) l else eval(n.r, env)
                "||" -> if (truthy(l)) l else eval(n.r, env)
                else -> if (isNullish(l)) eval(n.r, env) else l
            }
        }
        is CondE -> if (truthy(eval(n.test, env))) eval(n.a, env) else eval(n.b, env)
        is AssignE -> {
            val value = if (n.op == "=") eval(n.value, env)
            else binary(n.op.dropLast(1), read(n.target, env), eval(n.value, env), n.line)
            write(n.target, value, env)
            value
        }
        is UpdateE -> {
            val old = toNum(read(n.target, env))
            val new = if (n.op == "++") old + 1 else old - 1
            write(n.target, new, env)
            if (n.prefix) new else old
        }
        is FnE -> JsFunction(n, env)
        else -> throw ScriptError("Can't evaluate this here", n.line)
    }

    private fun propKey(m: MemberE, env: Env): String =
        if (m.computed) toStr(eval(m.prop, env)) else (m.prop as StrE).v

    private fun read(target: Node, env: Env): Any? = eval(target, env)

    private fun write(target: Node, value: Any?, env: Env) {
        when (target) {
            is IdentE -> {
                val b = env.lookup(target.name) ?: throw ScriptError("'${target.name}' is not declared (use let)", target.line)
                if (b.const) throw ScriptError("Can't reassign const '${target.name}'", target.line)
                b.value = value
            }
            is MemberE -> setProp(eval(target.obj, env), propKey(target, env), value, target.line)
            else -> throw ScriptError("Can't assign to this expression", target.line)
        }
    }

    private fun evalCall(n: CallE, env: Env): Any? {
        val callee = n.callee
        val fn: Any?
        if (callee is MemberE) {
            val obj = eval(callee.obj, env)
            if (callee.optional && isNullish(obj)) throw ShortCircuit
            fn = getProp(obj, propKey(callee, env), callee.line)
        } else {
            fn = eval(callee, env)
        }
        if (n.optional && isNullish(fn)) throw ShortCircuit
        val args = n.args.map { eval(it, env) }
        return call(fn, args, n.line, describe(callee))
    }

    private fun describe(n: Node): String = when (n) {
        is IdentE -> n.name
        is MemberE -> if (!n.computed) "${describe(n.obj)}.${(n.prop as StrE).v}" else "${describe(n.obj)}[…]"
        else -> "expression"
    }

    fun call(fn: Any?, args: List<Any?>, line: Int, name: String = "value"): Any? = when (fn) {
        is NativeFn -> try {
            fn.impl(args)
        } catch (e: ScriptError) {
            throw if (e.line == 0) ScriptError(e.message ?: "Error", line) else e
        }
        is JsFunction -> {
            if (++depth > 64) throw ScriptError("Too much recursion", line)
            try {
                val local = Env(fn.closure)
                fn.fn.params.forEachIndexed { i, p -> local.declareVar(p, args.getOrElse(i) { Undefined }) }
                if (fn.fn.exprBody) eval(fn.fn.body, local)
                else {
                    val body = fn.fn.body as BlockS
                    hoist(body.body, local)
                    try {
                        for (s in body.body) exec(s, local)
                        Undefined
                    } catch (r: ReturnSignal) {
                        r.value
                    }
                }
            } finally {
                depth--
            }
        }
        else -> throw ScriptError("$name is not a function", line)
    }

    private fun getProp(obj: Any?, key: String, line: Int): Any? = when (obj) {
        null, Undefined -> throw ScriptError("Can't read '$key' of ${toStr(obj)}", line)
        is JsObject -> if (obj.props.containsKey(key)) obj.props[key] else Undefined
        is JsArray -> {
            val idx = key.toIntOrNull()
            when {
                key == "length" -> obj.items.size.toDouble()
                idx != null -> obj.items.getOrElse(idx) { Undefined }
                else -> arrayMethod(obj, key, line) ?: Undefined
            }
        }
        is String -> {
            val idx = key.toIntOrNull()
            when {
                key == "length" -> obj.length.toDouble()
                idx != null -> obj.getOrNull(idx)?.toString() ?: Undefined
                else -> stringMethod(obj, key) ?: Undefined
            }
        }
        is Double -> when (key) {
            "toFixed" -> NativeFn("toFixed") { a ->
                val digits = num(a, 0, 0.0).toInt().coerceIn(0, 20)
                String.format(Locale.US, "%.${digits}f", obj)
            }
            "toString" -> NativeFn("toString") { toStr(obj) }
            else -> Undefined
        }
        is JsCallable -> if (key == "name") obj.name else Undefined
        else -> Undefined
    }

    private fun setProp(obj: Any?, key: String, value: Any?, line: Int) {
        when (obj) {
            is JsObject -> {
                if (!obj.props.containsKey(key) && obj.props.size >= 1000) throw ScriptError("Object too large", line)
                obj.props[key] = value
            }
            is JsArray -> {
                val idx = key.toIntOrNull() ?: throw ScriptError("Can't set '$key' on an array", line)
                if (idx < 0 || idx > 10_000) throw ScriptError("Array index out of range", line)
                while (obj.items.size <= idx) obj.items.add(Undefined)
                obj.items[idx] = value
            }
            else -> throw ScriptError("Can't set '$key' on ${typeOf(obj)}", line)
        }
    }

    private fun arg(a: List<Any?>, i: Int): Any? = a.getOrElse(i) { Undefined }
    private fun str(a: List<Any?>, i: Int): String = toStr(arg(a, i))
    private fun num(a: List<Any?>, i: Int, default: Double): Double {
        val v = arg(a, i)
        return if (v === Undefined) default else toNum(v)
    }

    private fun rel(i: Double, len: Int): Int {
        val n = if (i.isNaN()) 0 else i.toInt()
        return if (n < 0) maxOf(0, len + n) else minOf(n, len)
    }

    private fun stringMethod(s: String, key: String): NativeFn? = when (key) {
        "includes" -> NativeFn(key) { a -> s.contains(str(a, 0)) }
        "startsWith" -> NativeFn(key) { a -> s.startsWith(str(a, 0)) }
        "endsWith" -> NativeFn(key) { a -> s.endsWith(str(a, 0)) }
        "indexOf" -> NativeFn(key) { a -> s.indexOf(str(a, 0)).toDouble() }
        "toLowerCase" -> NativeFn(key) { s.lowercase() }
        "toUpperCase" -> NativeFn(key) { s.uppercase() }
        "trim" -> NativeFn(key) { s.trim() }
        "charAt" -> NativeFn(key) { a -> s.getOrNull(num(a, 0, 0.0).toInt())?.toString() ?: "" }
        "at" -> NativeFn(key) { a ->
            val i = num(a, 0, 0.0).toInt()
            s.getOrNull(if (i < 0) s.length + i else i)?.toString() ?: Undefined
        }
        "slice" -> NativeFn(key) { a ->
            val from = rel(num(a, 0, 0.0), s.length)
            val to = rel(num(a, 1, s.length.toDouble()), s.length)
            if (from < to) s.substring(from, to) else ""
        }
        "substring" -> NativeFn(key) { a ->
            val x = num(a, 0, 0.0).toInt().coerceIn(0, s.length)
            val y = num(a, 1, s.length.toDouble()).toInt().coerceIn(0, s.length)
            s.substring(minOf(x, y), maxOf(x, y))
        }
        "split" -> NativeFn(key) { a ->
            val sep = arg(a, 0)
            JsArray(
                when {
                    sep === Undefined -> mutableListOf<Any?>(s)
                    toStr(sep).isEmpty() -> s.map { it.toString() }.toMutableList()
                    else -> s.split(toStr(sep)).toMutableList()
                }
            )
        }
        "replace" -> NativeFn(key) { a -> s.replaceFirst(str(a, 0), str(a, 1)) }
        "replaceAll" -> NativeFn(key) { a -> s.replace(str(a, 0), str(a, 1)) }
        "repeat" -> NativeFn(key) { a ->
            val n = num(a, 0, 0.0).toInt()
            if (n < 0 || s.length.toLong() * n > 100_000) throw ScriptError("repeat() result too long")
            s.repeat(n)
        }
        "padStart" -> NativeFn(key) { a ->
            val pad = if (arg(a, 1) === Undefined) ' ' else str(a, 1).firstOrNull() ?: ' '
            s.padStart(num(a, 0, 0.0).toInt().coerceAtMost(10_000), pad)
        }
        "toString" -> NativeFn(key) { s }
        else -> null
    }

    private fun arrayMethod(arr: JsArray, key: String, line: Int): NativeFn? {
        val items = arr.items
        fun each(a: List<Any?>, f: (Any?, Any?) -> Boolean) {
            val cb = arg(a, 0)
            for ((i, item) in items.toList().withIndex()) {
                if (!f(item, call(cb, listOf(item, i.toDouble(), arr), line, "callback"))) return
            }
        }
        return when (key) {
            "push" -> NativeFn(key) { a ->
                if (items.size + a.size > 10_000) throw ScriptError("Array too large")
                items.addAll(a); items.size.toDouble()
            }
            "pop" -> NativeFn(key) { if (items.isEmpty()) Undefined else items.removeAt(items.size - 1) }
            "shift" -> NativeFn(key) { if (items.isEmpty()) Undefined else items.removeAt(0) }
            "includes" -> NativeFn(key) { a -> items.any { strictEq(it, arg(a, 0)) } }
            "indexOf" -> NativeFn(key) { a -> items.indexOfFirst { strictEq(it, arg(a, 0)) }.toDouble() }
            "join" -> NativeFn(key) { a ->
                val sep = if (arg(a, 0) === Undefined) "," else str(a, 0)
                items.joinToString(sep) { if (isNullish(it)) "" else toStr(it) }
            }
            "slice" -> NativeFn(key) { a ->
                val from = rel(num(a, 0, 0.0), items.size)
                val to = rel(num(a, 1, items.size.toDouble()), items.size)
                JsArray(if (from < to) items.subList(from, to).toMutableList() else mutableListOf())
            }
            "concat" -> NativeFn(key) { a ->
                val out = items.toMutableList()
                for (x in a) if (x is JsArray) out.addAll(x.items) else out.add(x)
                JsArray(out)
            }
            "reverse" -> NativeFn(key) { items.reverse(); arr }
            "map" -> NativeFn(key) { a ->
                val out = mutableListOf<Any?>()
                each(a) { _, r -> out.add(r); true }
                JsArray(out)
            }
            "filter" -> NativeFn(key) { a ->
                val out = mutableListOf<Any?>()
                each(a) { item, r -> if (truthy(r)) out.add(item); true }
                JsArray(out)
            }
            "forEach" -> NativeFn(key) { a -> each(a) { _, _ -> true }; Undefined }
            "some" -> NativeFn(key) { a -> var hit = false; each(a) { _, r -> hit = truthy(r); !hit }; hit }
            "every" -> NativeFn(key) { a -> var ok = true; each(a) { _, r -> ok = truthy(r); ok }; ok }
            "find" -> NativeFn(key) { a -> var found: Any? = Undefined; each(a) { item, r -> if (truthy(r)) { found = item; false } else true }; found }
            "findIndex" -> NativeFn(key) { a ->
                var idx = -1; var i = 0
                each(a) { _, r -> if (truthy(r)) { idx = i; false } else { i++; true } }
                idx.toDouble()
            }
            "sort" -> NativeFn(key) { a ->
                val cmp = arg(a, 0)
                val sorted = if (cmp === Undefined) items.sortedBy { toStr(it) }
                else items.sortedWith { x, y -> toNum(call(cmp, listOf(x, y), line, "comparator")).let { if (it < 0) -1 else if (it > 0) 1 else 0 } }
                items.clear(); items.addAll(sorted); arr
            }
            else -> null
        }
    }

    private fun installBuiltins() {
        fun def(name: String, value: Any?) = globals.declare(name, value, true, 0)
        fun fn(name: String, impl: (List<Any?>) -> Any?) = NativeFn(name, impl)

        def("Math", JsObject(linkedMapOf<String, Any?>(
            "PI" to Math.PI,
            "random" to fn("random") { Random.nextDouble() },
            "floor" to fn("floor") { a -> floor(num(a, 0, Double.NaN)) },
            "ceil" to fn("ceil") { a -> kotlin.math.ceil(num(a, 0, Double.NaN)) },
            "round" to fn("round") { a -> floor(num(a, 0, Double.NaN) + 0.5) },
            "abs" to fn("abs") { a -> abs(num(a, 0, Double.NaN)) },
            "sqrt" to fn("sqrt") { a -> kotlin.math.sqrt(num(a, 0, Double.NaN)) },
            "pow" to fn("pow") { a -> num(a, 0, Double.NaN).pow(num(a, 1, Double.NaN)) },
            "min" to fn("min") { a -> a.minOfOrNull { toNum(it) } ?: Double.POSITIVE_INFINITY },
            "max" to fn("max") { a -> a.maxOfOrNull { toNum(it) } ?: Double.NEGATIVE_INFINITY },
        )))
        def("String", fn("String") { a -> if (a.isEmpty()) "" else str(a, 0) })
        def("Number", fn("Number") { a -> if (a.isEmpty()) 0.0 else toNum(a[0]) })
        def("Boolean", fn("Boolean") { a -> truthy(arg(a, 0)) })
        def("isNaN", fn("isNaN") { a -> toNum(arg(a, 0)).isNaN() })
        def("parseInt", fn("parseInt") { a ->
            Regex("""^\s*[-+]?\d+""").find(str(a, 0))?.value?.trim()?.toDoubleOrNull() ?: Double.NaN
        })
        def("parseFloat", fn("parseFloat") { a ->
            Regex("""^\s*[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?""").find(str(a, 0))?.value?.trim()?.toDoubleOrNull() ?: Double.NaN
        })
        def("JSON", JsObject(linkedMapOf<String, Any?>("stringify" to fn("stringify") { a -> display(arg(a, 0), quoteStrings = true) })))
        def("Array", JsObject(linkedMapOf<String, Any?>("isArray" to fn("isArray") { a -> arg(a, 0) is JsArray })))
    }

    private fun capString(s: String, line: Int): String {
        if (s.length > 100_000) throw ScriptError("String too long", line)
        return s
    }

    private fun binary(op: String, l: Any?, r: Any?, line: Int): Any? = when (op) {
        "+" -> if (l is String || r is String || l is JsObject || r is JsObject || l is JsArray || r is JsArray) {
            capString(toStr(l) + toStr(r), line)
        } else toNum(l) + toNum(r)
        "-" -> toNum(l) - toNum(r)
        "*" -> toNum(l) * toNum(r)
        "/" -> toNum(l) / toNum(r)
        "%" -> toNum(l) % toNum(r)
        "**" -> toNum(l).pow(toNum(r))
        "===" -> strictEq(l, r)
        "!==" -> !strictEq(l, r)
        "==" -> looseEq(l, r)
        "!=" -> !looseEq(l, r)
        "<", ">", "<=", ">=" -> {
            val c: Int? = if (l is String && r is String) l.compareTo(r) else {
                val a = toNum(l); val b = toNum(r)
                if (a.isNaN() || b.isNaN()) null else a.compareTo(b)
            }
            if (c == null) false else when (op) { "<" -> c < 0; ">" -> c > 0; "<=" -> c <= 0; else -> c >= 0 }
        }
        else -> throw ScriptError("Unknown operator $op", line)
    }

    companion object {
        fun isNullish(v: Any?) = v == null || v === Undefined

        fun truthy(v: Any?): Boolean = when (v) {
            null, Undefined -> false
            is Boolean -> v
            is Double -> !(v == 0.0 || v.isNaN())
            is String -> v.isNotEmpty()
            else -> true
        }

        fun toNum(v: Any?): Double = when (v) {
            is Double -> v
            is Boolean -> if (v) 1.0 else 0.0
            null -> 0.0
            is String -> v.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }
            else -> Double.NaN
        }

        fun toStr(v: Any?): String = when (v) {
            null -> "null"
            Undefined -> "undefined"
            is Double -> TemplateNum.format(v)
            is String -> v
            is Boolean -> v.toString()
            is JsArray -> v.items.joinToString(",") { if (isNullish(it)) "" else toStr(it) }
            is JsObject -> "[object Object]"
            is JsCallable -> "function ${v.name}() { … }"
            else -> v.toString()
        }

        fun typeOf(v: Any?): String = when (v) {
            Undefined -> "undefined"
            null -> "object"
            is Boolean -> "boolean"
            is Double -> "number"
            is String -> "string"
            is JsCallable -> "function"
            else -> "object"
        }

        fun strictEq(l: Any?, r: Any?): Boolean = when {
            l == null || r == null -> l == null && r == null
            l === Undefined || r === Undefined -> l === r
            l is Double && r is Double -> { val a: Double = l; val b: Double = r; a == b }
            l is String && r is String -> l == r
            l is Boolean && r is Boolean -> l == r
            else -> l === r
        }

        fun looseEq(l: Any?, r: Any?): Boolean = when {
            isNullish(l) && isNullish(r) -> true
            isNullish(l) || isNullish(r) -> false
            (l is Double || l is String || l is Boolean) && (r is Double || r is String || r is Boolean) &&
                l!!::class != r!!::class -> { val a = toNum(l); val b = toNum(r); a == b }
            else -> strictEq(l, r)
        }

        fun display(v: Any?, quoteStrings: Boolean = false, depth: Int = 0): String = when (v) {
            is String -> if (quoteStrings) "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"" else v
            is JsArray -> if (depth > 4) "[…]" else v.items.joinToString(",", "[", "]") { display(it, true, depth + 1) }
            is JsObject -> if (depth > 4) "{…}" else v.props.entries.joinToString(",", "{", "}") { (k, x) -> "\"$k\":${display(x, true, depth + 1)}" }
            else -> toStr(v)
        }
    }
}
