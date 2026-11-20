package app.pushcraft.script

sealed class Node(val line: Int)

class NumE(val v: Double, line: Int) : Node(line)
class StrE(val v: String, line: Int) : Node(line)
class LitE(val v: Any?, line: Int) : Node(line)
class TplE(val parts: List<Node>, line: Int) : Node(line)
class IdentE(val name: String, line: Int) : Node(line)
class ObjE(val props: List<Pair<String, Node>>, line: Int) : Node(line)
class ArrE(val items: List<Node>, line: Int) : Node(line)
class MemberE(val obj: Node, val prop: Node, val computed: Boolean, val optional: Boolean, line: Int) : Node(line)
class CallE(val callee: Node, val args: List<Node>, val optional: Boolean, line: Int) : Node(line)
class ChainE(val e: Node, line: Int) : Node(line)
class UnaryE(val op: String, val arg: Node, line: Int) : Node(line)
class BinE(val op: String, val l: Node, val r: Node, line: Int) : Node(line)
class LogicE(val op: String, val l: Node, val r: Node, line: Int) : Node(line)
class CondE(val test: Node, val a: Node, val b: Node, line: Int) : Node(line)
class AssignE(val op: String, val target: Node, val value: Node, line: Int) : Node(line)
class UpdateE(val op: String, val prefix: Boolean, val target: Node, line: Int) : Node(line)
class FnE(val name: String?, val params: List<String>, val body: Node, val exprBody: Boolean, line: Int) : Node(line)

class VarS(val kind: String, val decls: List<Pair<String, Node?>>, line: Int) : Node(line)
class ExprS(val e: Node, line: Int) : Node(line)
class IfS(val test: Node, val then: Node, val otherwise: Node?, line: Int) : Node(line)
class WhileS(val test: Node, val body: Node, line: Int) : Node(line)
class ForS(val init: Node?, val test: Node?, val update: Node?, val body: Node, line: Int) : Node(line)
class ForOfS(val kind: String, val name: String, val iter: Node, val body: Node, line: Int) : Node(line)
class BlockS(val body: List<Node>, line: Int) : Node(line)
class ReturnS(val arg: Node?, line: Int) : Node(line)
class BreakS(line: Int) : Node(line)
class ContinueS(line: Int) : Node(line)
class FnDeclS(val fn: FnE, line: Int) : Node(line)
class EmptyS(line: Int) : Node(line)

class Parser(private val toks: List<Token>) {
    private var p = 0
    private val cur: Token get() = toks[p]
    private fun peek(k: Int = 1): Token = toks[minOf(p + k, toks.size - 1)]
    private fun isOp(s: String) = cur.type == T.OP && cur.text == s
    private fun isKw(s: String) = cur.type == T.KW && cur.text == s
    private fun eatOp(s: String): Boolean = if (isOp(s)) { p++; true } else false
    private fun err(msg: String): Nothing = throw ScriptError(msg, cur.line)
    private fun expectOp(s: String): Token {
        if (!isOp(s)) err("Expected '$s' but found ${cur.describe()}")
        return toks[p++]
    }
    private fun ident(): String {
        if (cur.type != T.IDENT) err("Expected a name but found ${cur.describe()}")
        return toks[p++].text
    }

    companion object {
        fun parse(source: String): BlockS = Parser(Lexer(source).tokens()).program()

        private val PREC = mapOf(
            "??" to 1, "||" to 2, "&&" to 3,
            "==" to 6, "!=" to 6, "===" to 6, "!==" to 6,
            "<" to 7, ">" to 7, "<=" to 7, ">=" to 7,
            "+" to 9, "-" to 9, "*" to 10, "/" to 10, "%" to 10, "**" to 11,
        )
        private val ASSIGN = setOf("=", "+=", "-=", "*=", "/=", "%=")
    }

    fun program(): BlockS {
        val body = mutableListOf<Node>()
        while (cur.type != T.EOF) body += statement()
        return BlockS(body, 1)
    }

    private fun semi() {
        if (eatOp(";")) return
        if (isOp("}") || cur.type == T.EOF || cur.line > toks[p - 1].line) return
        err("Expected ';' or a new line before ${cur.describe()}")
    }

    private fun statement(): Node {
        val line = cur.line
        return when {
            isOp("{") -> block()
            isOp(";") -> { p++; EmptyS(line) }
            isKw("let") || isKw("const") || isKw("var") -> varDecl().also { semi() }
            isKw("if") -> {
                p++; expectOp("(")
                val test = expression()
                expectOp(")")
                val then = statement()
                val other = if (isKw("else")) { p++; statement() } else null
                IfS(test, then, other, line)
            }
            isKw("while") -> {
                p++; expectOp("(")
                val test = expression()
                expectOp(")")
                WhileS(test, statement(), line)
            }
            isKw("for") -> forStatement()
            isKw("return") -> {
                p++
                val arg = if (isOp(";") || isOp("}") || cur.type == T.EOF || cur.line != line) null else expression()
                semi()
                ReturnS(arg, line)
            }
            isKw("break") -> { p++; semi(); BreakS(line) }
            isKw("continue") -> { p++; semi(); ContinueS(line) }
            isKw("function") && peek().type == T.IDENT -> FnDeclS(function(), line)
            else -> ExprS(expression(), line).also { semi() }
        }
    }

    private fun block(): BlockS {
        val line = expectOp("{").line
        val body = mutableListOf<Node>()
        while (!isOp("}")) {
            if (cur.type == T.EOF) err("Missing '}' for the block opened on line $line")
            body += statement()
        }
        p++
        return BlockS(body, line)
    }

    private fun varDecl(): VarS {
        val line = cur.line
        val kind = toks[p++].text
        val decls = mutableListOf<Pair<String, Node?>>()
        do {
            val name = ident()
            val init = if (eatOp("=")) assignment() else null
            if (kind == "const" && init == null) err("const '$name' needs a value")
            decls += name to init
        } while (eatOp(","))
        return VarS(kind, decls, line)
    }

    private fun forStatement(): Node {
        val line = cur.line
        p++
        expectOp("(")
        val declares = isKw("let") || isKw("const") || isKw("var")
        if (declares && peek().type == T.IDENT && peek(2).type == T.IDENT && peek(2).text == "of") {
            val kind = toks[p++].text
            val name = ident()
            p++
            val iter = expression()
            expectOp(")")
            return ForOfS(kind, name, iter, statement(), line)
        }
        val init = when {
            isOp(";") -> null
            declares -> varDecl()
            else -> ExprS(expression(), line)
        }
        expectOp(";")
        val test = if (isOp(";")) null else expression()
        expectOp(";")
        val update = if (isOp(")")) null else expression()
        expectOp(")")
        return ForS(init, test, update, statement(), line)
    }

    private fun function(): FnE {
        val line = cur.line
        p++
        val name = if (cur.type == T.IDENT) toks[p++].text else null
        expectOp("(")
        val params = mutableListOf<String>()
        while (!isOp(")")) {
            params += ident()
            if (!eatOp(",")) break
        }
        expectOp(")")
        return FnE(name, params, block(), exprBody = false, line = line)
    }

    fun expression(): Node = assignment()

    private fun arrowAhead(): Boolean {
        if (cur.type == T.IDENT && peek().type == T.OP && peek().text == "=>") return true
        if (!isOp("(")) return false
        var depth = 0
        var k = p
        while (k < toks.size) {
            val t = toks[k]
            if (t.type == T.EOF) return false
            if (t.type == T.OP && t.text == "(") depth++
            if (t.type == T.OP && t.text == ")") {
                depth--
                if (depth == 0) {
                    val after = toks.getOrNull(k + 1)
                    return after != null && after.type == T.OP && after.text == "=>"
                }
            }
            k++
        }
        return false
    }

    private fun arrow(): FnE {
        val line = cur.line
        val params = mutableListOf<String>()
        if (cur.type == T.IDENT) {
            params += toks[p++].text
        } else {
            expectOp("(")
            while (!isOp(")")) {
                params += ident()
                if (!eatOp(",")) break
            }
            expectOp(")")
        }
        expectOp("=>")
        return if (isOp("{")) FnE(null, params, block(), exprBody = false, line = line)
        else FnE(null, params, assignment(), exprBody = true, line = line)
    }

    private fun assignment(): Node {
        if (arrowAhead()) return arrow()
        val left = conditional()
        if (cur.type == T.OP && cur.text in ASSIGN) {
            val op = toks[p++]
            if (left !is IdentE && left !is MemberE) throw ScriptError("Can't assign to this expression", op.line)
            return AssignE(op.text, left, assignment(), op.line)
        }
        return left
    }

    private fun conditional(): Node {
        val test = binary(1)
        if (isOp("?")) {
            val line = toks[p++].line
            val a = assignment()
            expectOp(":")
            val b = assignment()
            return CondE(test, a, b, line)
        }
        return test
    }

    private fun binary(minPrec: Int): Node {
        var left = unary()
        while (true) {
            val op = cur
            val prec = if (op.type == T.OP) PREC[op.text] else null
            if (prec == null || prec < minPrec) return left
            p++
            val right = binary(if (op.text == "**") prec else prec + 1)
            left = if (op.text == "&&" || op.text == "||" || op.text == "??") LogicE(op.text, left, right, op.line)
            else BinE(op.text, left, right, op.line)
        }
    }

    private fun unary(): Node {
        val t = cur
        if (t.type == T.OP && (t.text == "!" || t.text == "-" || t.text == "+")) { p++; return UnaryE(t.text, unary(), t.line) }
        if (t.type == T.KW && t.text == "typeof") { p++; return UnaryE("typeof", unary(), t.line) }
        if (t.type == T.OP && (t.text == "++" || t.text == "--")) { p++; return UpdateE(t.text, true, unary(), t.line) }
        val e = callChain()
        if ((isOp("++") || isOp("--")) && cur.line == toks[p - 1].line) {
            val op = toks[p++]
            return UpdateE(op.text, false, e, op.line)
        }
        return e
    }

    private fun args(): List<Node> {
        expectOp("(")
        val out = mutableListOf<Node>()
        while (!isOp(")")) {
            out += assignment()
            if (!eatOp(",")) break
        }
        expectOp(")")
        return out
    }

    private fun propName(): String {
        val t = cur
        if (t.type != T.IDENT && t.type != T.KW) err("Expected a property name but found ${t.describe()}")
        p++
        return t.text
    }

    private fun callChain(): Node {
        var e = primary()
        var chained = false
        while (true) {
            val line = cur.line
            e = when {
                isOp(".") -> { p++; MemberE(e, StrE(propName(), line), computed = false, optional = false, line = line) }
                isOp("?.") -> {
                    p++
                    chained = true
                    when {
                        isOp("(") -> CallE(e, args(), optional = true, line = line)
                        isOp("[") -> {
                            p++
                            val idx = expression()
                            expectOp("]")
                            MemberE(e, idx, computed = true, optional = true, line = line)
                        }
                        else -> MemberE(e, StrE(propName(), line), computed = false, optional = true, line = line)
                    }
                }
                isOp("[") -> {
                    p++
                    val idx = expression()
                    expectOp("]")
                    MemberE(e, idx, computed = true, optional = false, line = line)
                }
                isOp("(") -> CallE(e, args(), optional = false, line = line)
                else -> return if (chained) ChainE(e, e.line) else e
            }
        }
    }

    private fun primary(): Node {
        val t = cur
        return when (t.type) {
            T.NUM -> { p++; NumE(t.num, t.line) }
            T.STR -> { p++; StrE(t.text, t.line) }
            T.TEMPLATE -> {
                p++
                TplE(t.parts.map { part ->
                    when (part) {
                        is TPart.Lit -> StrE(part.text, t.line)
                        is TPart.Code -> {
                            val sub = Parser(Lexer(part.src).tokens())
                            val expr = sub.expression()
                            if (sub.cur.type != T.EOF) throw ScriptError("Unexpected ${sub.cur.describe()} inside \${…}", part.line)
                            expr
                        }
                    }
                }, t.line)
            }
            T.IDENT -> { p++; IdentE(t.text, t.line) }
            T.KW -> when (t.text) {
                "true" -> { p++; LitE(true, t.line) }
                "false" -> { p++; LitE(false, t.line) }
                "null" -> { p++; LitE(null, t.line) }
                "undefined" -> { p++; LitE(Undefined, t.line) }
                "function" -> function()
                else -> err("Unexpected '${t.text}'")
            }
            T.OP -> when (t.text) {
                "(" -> { p++; val e = expression(); expectOp(")"); e }
                "[" -> {
                    p++
                    val items = mutableListOf<Node>()
                    while (!isOp("]")) {
                        items += assignment()
                        if (!eatOp(",")) break
                    }
                    expectOp("]")
                    ArrE(items, t.line)
                }
                "{" -> {
                    p++
                    val props = mutableListOf<Pair<String, Node>>()
                    while (!isOp("}")) {
                        val k = cur
                        val key = when (k.type) {
                            T.IDENT, T.KW, T.STR -> k.text
                            T.NUM -> TemplateNum.format(k.num)
                            else -> err("Expected a property name but found ${k.describe()}")
                        }
                        p++
                        val value = if (eatOp(":")) assignment()
                        else if (k.type == T.IDENT) IdentE(key, k.line)
                        else err("Expected ':' after '$key'")
                        props += key to value
                        if (!eatOp(",")) break
                    }
                    expectOp("}")
                    ObjE(props, t.line)
                }
                else -> err("Unexpected '${t.text}'")
            }
            T.EOF -> err("Unexpected end of script")
        }
    }
}

internal object TemplateNum {
    fun format(d: Double): String = when {
        d.isNaN() -> "NaN"
        d == Double.POSITIVE_INFINITY -> "Infinity"
        d == Double.NEGATIVE_INFINITY -> "-Infinity"
        d == Math.floor(d) && kotlin.math.abs(d) < 1e21 -> if (d == 0.0) "0" else d.toLong().toString()
        else -> d.toString()
    }
}
