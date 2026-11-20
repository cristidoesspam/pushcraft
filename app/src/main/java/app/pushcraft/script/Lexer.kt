package app.pushcraft.script

class ScriptError(message: String, val line: Int = 0) : Exception(message)

enum class T { NUM, STR, TEMPLATE, IDENT, KW, OP, EOF }

sealed class TPart {
    data class Lit(val text: String) : TPart()
    data class Code(val src: String, val line: Int) : TPart()
}

class Token(
    val type: T,
    val text: String,
    val line: Int,
    val num: Double = 0.0,
    val parts: List<TPart> = emptyList(),
) {
    fun describe(): String = when (type) {
        T.EOF -> "end of script"
        T.STR -> "string"
        T.TEMPLATE -> "template string"
        T.NUM -> "number $text"
        else -> "'$text'"
    }
}

class Lexer(source: String) {
    private val src = source
        .replace('‘', '\'').replace('’', '\'')
        .replace('“', '"').replace('”', '"')
        .replace(' ', ' ')
    private var i = 0
    private var line = 1

    companion object {
        val KEYWORDS = setOf(
            "let", "const", "var", "if", "else", "while", "for", "return", "break", "continue",
            "true", "false", "null", "undefined", "function", "typeof",
        )
        private val OPS = listOf(
            "===", "!==", "**", "=>", "==", "!=", "<=", ">=", "&&", "||", "??", "?.", "++", "--",
            "+=", "-=", "*=", "/=", "%=",
            "{", "}", "(", ")", "[", "]", ";", ",", ".", ":", "?", "+", "-", "*", "/", "%", "<", ">", "=", "!",
        )
    }

    fun tokens(): List<Token> {
        val out = mutableListOf<Token>()
        while (true) {
            val t = next()
            out += t
            if (t.type == T.EOF) return out
        }
    }

    private fun next(): Token {
        skipSpaceAndComments()
        if (i >= src.length) return Token(T.EOF, "", line)
        val c = src[i]
        if (c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit())) return number()
        if (c == '"' || c == '\'') return string(c)
        if (c == '`') return template()
        if (c.isLetter() || c == '_' || c == '$') {
            val start = i
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '$')) i++
            val word = src.substring(start, i)
            return Token(if (word in KEYWORDS) T.KW else T.IDENT, word, line)
        }
        for (op in OPS) {
            if (src.startsWith(op, i)) {
                if (op == "?." && i + 2 < src.length && src[i + 2].isDigit()) continue
                i += op.length
                return Token(T.OP, op, line)
            }
        }
        throw ScriptError("Unexpected character '$c'", line)
    }

    private fun skipSpaceAndComments() {
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\n' -> { line++; i++ }
                c.isWhitespace() -> i++
                src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') i++
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2)
                    if (end < 0) throw ScriptError("Unclosed /* comment", line)
                    line += src.substring(i, end).count { it == '\n' }
                    i = end + 2
                }
                else -> return
            }
        }
    }

    private fun number(): Token {
        val start = i
        if (src.startsWith("0x", i) || src.startsWith("0X", i)) {
            i += 2
            while (i < src.length && (src[i].isDigit() || src[i].lowercaseChar() in 'a'..'f')) i++
            val text = src.substring(start, i)
            return Token(T.NUM, text, line, text.drop(2).toLong(16).toDouble())
        }
        while (i < src.length && (src[i].isDigit() || src[i] == '_')) i++
        if (i < src.length && src[i] == '.') { i++; while (i < src.length && src[i].isDigit()) i++ }
        if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
            i++
            if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
            while (i < src.length && src[i].isDigit()) i++
        }
        val text = src.substring(start, i)
        val value = text.replace("_", "").toDoubleOrNull() ?: throw ScriptError("Bad number '$text'", line)
        return Token(T.NUM, text, line, value)
    }

    private fun escape(sb: StringBuilder) {
        if (i >= src.length) throw ScriptError("Unterminated string", line)
        when (val e = src[i++]) {
            'n' -> sb.append('\n')
            't' -> sb.append('\t')
            'r' -> sb.append('\r')
            '0' -> sb.append('\u0000')
            'u' -> {
                if (i < src.length && src[i] == '{') {
                    val end = src.indexOf('}', i)
                    if (end < 0) throw ScriptError("Bad \\u{…} escape", line)
                    sb.appendCodePoint(src.substring(i + 1, end).toInt(16)); i = end + 1
                } else {
                    if (i + 4 > src.length) throw ScriptError("Bad \\u escape", line)
                    sb.append(src.substring(i, i + 4).toInt(16).toChar()); i += 4
                }
            }
            '\n' -> line++
            else -> sb.append(e)
        }
    }

    private fun string(quote: Char): Token {
        val startLine = line
        i++
        val sb = StringBuilder()
        while (true) {
            if (i >= src.length || src[i] == '\n') throw ScriptError("Unterminated string", startLine)
            val c = src[i++]
            when (c) {
                quote -> return Token(T.STR, sb.toString(), startLine)
                '\\' -> escape(sb)
                else -> sb.append(c)
            }
        }
    }

    private fun template(): Token {
        val startLine = line
        i++
        val parts = mutableListOf<TPart>()
        val sb = StringBuilder()
        while (true) {
            if (i >= src.length) throw ScriptError("Unterminated template string", startLine)
            val c = src[i]
            when {
                c == '`' -> {
                    i++
                    if (sb.isNotEmpty()) parts += TPart.Lit(sb.toString())
                    return Token(T.TEMPLATE, "`", startLine, parts = parts)
                }
                c == '\\' -> { i++; escape(sb) }
                c == '$' && i + 1 < src.length && src[i + 1] == '{' -> {
                    if (sb.isNotEmpty()) { parts += TPart.Lit(sb.toString()); sb.clear() }
                    i += 2
                    val codeLine = line
                    val start = i
                    var depth = 1
                    var quote: Char? = null
                    while (i < src.length) {
                        val ch = src[i]
                        if (quote != null) {
                            if (ch == '\\') i++ else if (ch == quote) quote = null
                        } else when (ch) {
                            '\'', '"', '`' -> quote = ch
                            '{' -> depth++
                            '}' -> { depth--; if (depth == 0) break }
                            '\n' -> line++
                        }
                        i++
                    }
                    if (depth != 0) throw ScriptError("Unclosed \${ in template string", codeLine)
                    parts += TPart.Code(src.substring(start, i), codeLine)
                    i++
                }
                else -> {
                    if (c == '\n') line++
                    sb.append(c); i++
                }
            }
        }
    }
}
