package app.pushcraft.ui.script

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pushcraft.script.Effect
import app.pushcraft.script.ScriptEvent
import app.pushcraft.script.ScriptExamples
import app.pushcraft.script.ScriptHost
import app.pushcraft.script.ScriptResult
import app.pushcraft.data.TemplateVars
import app.pushcraft.ui.theme.LocalGlass

private data class SyntaxColors(
    val keyword: Color, val string: Color, val number: Color, val comment: Color, val api: Color,
)

private val TOKEN = Regex(
    """(//[^\n]*|/\*[\s\S]*?(?:\*/|$))|('(?:\\.|[^'\\\n])*'?|"(?:\\.|[^"\\\n])*"?|`(?:\\.|[^`\\])*`?)|(\b\d+(?:\.\d+)?\b)|(\b(?:let|const|var|if|else|while|for|of|return|break|continue|function|true|false|null|undefined|typeof)\b)|(\b(?:notify|openUrl|openApp|snooze|log|pick|vars|event|Math|console)\b)"""
)

private class SyntaxHighlight(private val c: SyntaxColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val out = buildAnnotatedString {
            append(s)
            for (m in TOKEN.findAll(s)) {
                val style = when {
                    m.groups[1] != null -> SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
                    m.groups[2] != null -> SpanStyle(color = c.string)
                    m.groups[3] != null -> SpanStyle(color = c.number)
                    m.groups[4] != null -> SpanStyle(color = c.keyword, fontWeight = FontWeight.SemiBold)
                    else -> SpanStyle(color = c.api)
                }
                addStyle(style, m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

private fun autoIndent(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.text.length != old.text.length + 1 || !new.selection.collapsed) return new
    val pos = new.selection.start
    if (pos == 0 || new.text[pos - 1] != '\n') return new
    val lineStart = new.text.lastIndexOf('\n', pos - 2) + 1
    val prev = new.text.substring(lineStart, pos - 1)
    var indent = prev.takeWhile { it == ' ' }
    if (prev.trimEnd().let { it.endsWith("{") || it.endsWith("(") || it.endsWith("[") }) indent += "  "
    if (indent.isEmpty()) return new
    return TextFieldValue(new.text.substring(0, pos) + indent + new.text.substring(pos), TextRange(pos + indent.length))
}

private fun insertAt(v: TextFieldValue, snippet: String): TextFieldValue {
    val start = v.selection.min
    val end = v.selection.max
    val text = v.text.substring(0, start) + snippet + v.text.substring(end)
    return TextFieldValue(text, TextRange(start + snippet.length))
}

private val SNIPPETS = listOf(
    "event.input", "event.tapCount", "notify({ title: '', body: '' })", "openUrl('https://')",
    "snooze(10)", "vars.inc('count')", "vars.get('key')", "log()", "if () {\n  \n}",
)

@Composable
fun ScriptEditorDialog(
    initial: String,
    actionLabel: String,
    withInput: Boolean,
    title: String,
    body: String,
    vars: Map<String, Any?>,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    var result by remember { mutableStateOf<ScriptResult?>(null) }
    var simInput by remember { mutableStateOf("running late") }
    var simTaps by remember { mutableIntStateOf(1) }
    var simOpen by remember { mutableStateOf(true) }
    var examplesOpen by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val dark = LocalGlass.current.dark
    val colors = if (dark) SyntaxColors(Color(0xFFC792EA), Color(0xFFC3E88D), Color(0xFFF78C6C), Color(0xFF697098), Color(0xFF82AAFF))
    else SyntaxColors(Color(0xFF7C3AED), Color(0xFF15803D), Color(0xFFC2410C), Color(0xFF94A3B8), Color(0xFF2563EB))
    val highlight = remember(dark) { SyntaxHighlight(colors) }
    val codeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface)
    val errorLine = result?.error?.line?.takeIf { it > 0 }

    fun simulate() {
        result = ScriptHost.run(
            value.text,
            ScriptEvent(
                input = if (withInput) simInput else null,
                tapCount = simTaps,
                action = actionLabel,
                title = title,
                body = body,
            ),
            vars,
        )
        simOpen = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Discard") }
                    Column(Modifier.weight(1f)) {
                        Text("Script", style = MaterialTheme.typography.titleMedium)
                        Text("Runs when \"$actionLabel\" is tapped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { showHelp = true }) { Icon(Icons.Rounded.Info, "API reference") }
                    Box {
                        IconButton(onClick = { examplesOpen = true }) { Icon(Icons.Rounded.AutoAwesome, "Examples") }
                        DropdownMenu(expanded = examplesOpen, onDismissRequest = { examplesOpen = false }) {
                            ScriptExamples.all.forEach { ex ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(ex.title, fontWeight = FontWeight.SemiBold)
                                            Text(ex.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { value = TextFieldValue(ex.code); result = null; examplesOpen = false },
                                )
                            }
                        }
                    }
                    TextButton(onClick = { onDone(value.text) }) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Done")
                    }
                }

                val vScroll = rememberScrollState()
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (dark) Color(0xFF0F1117) else Color(0xFFFBFBFE))
                        .verticalScroll(vScroll)
                        .padding(vertical = 12.dp)
                ) {
                    val lines = value.text.count { it == '\n' } + 1
                    Text(
                        (1..lines).joinToString("\n"),
                        style = codeStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), textAlign = TextAlign.End),
                        modifier = Modifier.width(34.dp).padding(end = 8.dp),
                    )
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val minW = maxWidth
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            BasicTextField(
                                value = value,
                                onValueChange = { value = autoIndent(value, it) },
                                textStyle = codeStyle,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = highlight,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Ascii,
                                ),
                                modifier = Modifier.widthIn(min = minW).heightIn(min = 240.dp).padding(end = 16.dp),
                            )
                        }
                    }
                }
                if (errorLine != null) {
                    Text(
                        "Line $errorLine: ${result?.error?.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SNIPPETS.forEach { s ->
                        AssistChip(
                            onClick = { value = insertAt(value, s) },
                            label = { Text(s.lineSequence().first().take(28), fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Simulate a tap", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { simOpen = !simOpen }, modifier = Modifier.size(32.dp)) {
                                Icon(if (simOpen) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, "Toggle simulator")
                            }
                        }
                        if (simOpen) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = if (withInput) simInput else "",
                                    onValueChange = { simInput = it },
                                    enabled = withInput,
                                    label = { Text(if (withInput) "event.input" else "No text box") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { if (simTaps > 1) simTaps-- }) { Icon(Icons.Rounded.Remove, "Fewer taps") }
                                Text("$simTaps", fontFamily = FontFamily.Monospace)
                                IconButton(onClick = { simTaps++ }) { Icon(Icons.Rounded.Add, "More taps") }
                            }
                            result?.let { ResultView(it) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { simulate() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Run")
                        }
                    }
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } },
            title = { Text("PushScript API") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("A small, sandboxed JavaScript. Scripts describe what should happen; Pushcraft checks and performs it.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Text(ScriptExamples.API_REFERENCE, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Title/body variables: " + TemplateVars.chips.joinToString(" "), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}

@Composable
private fun ResultView(r: ScriptResult) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val err = r.error
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (err == null) Icons.Rounded.Check else Icons.Rounded.ErrorOutline, null,
                tint = if (err == null) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (err == null) "Ran in ${r.millis} ms · ${r.effects.size} effect${if (r.effects.size == 1) "" else "s"}"
                else "Error${if (err.line > 0) " on line ${err.line}" else ""}: ${err.message}",
                style = MaterialTheme.typography.bodySmall,
                color = if (err == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
        r.effects.forEach { e ->
            val line = when (e) {
                is Effect.Notify -> "→ notify  \"${e.title}\" · ${e.body}"
                is Effect.OpenUrl -> "→ open    ${e.url}"
                is Effect.OpenApp -> "→ app     ${e.pkg}"
                is Effect.Snooze -> "→ snooze  ${e.minutes} min"
            }
            Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        r.logs.forEach { Text("log  $it", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
        if (err == null && r.vars.isNotEmpty()) {
            Text(
                "vars  " + r.vars.entries.joinToString("  ") { (k, v) -> "$k=${TemplateVars.displayValue(v)}" },
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            )
        }
    }
}
