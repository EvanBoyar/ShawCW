package com.shawcw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A minimal Markdown renderer, enough for the bundled README: headings, bullet
 * lists, paragraphs, fenced code blocks, and dividers, with inline bold, code,
 * and links. It is not a full CommonMark parser; the README is authored to the
 * subset handled here.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier) {
        for (block in parseBlocks(markdown)) {
            when (block) {
                is Block.Heading -> Text(
                    text = inline(block.text, linkColor, codeBg),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                )
                is Block.Paragraph -> Text(
                    text = inline(block.text, linkColor, codeBg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                is Block.Bullet -> Row(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        "•  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = inline(block.text, linkColor, codeBg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is Block.Code -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(codeBg)
                        .padding(12.dp),
                )
                Block.Divider -> HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Code(val text: String) : Block
    data object Divider : Block
}

private fun parseBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val para = StringBuilder()
    val code = StringBuilder()
    var inCode = false

    fun flushPara() {
        if (para.isNotBlank()) blocks.add(Block.Paragraph(para.toString().trim()))
        para.setLength(0)
    }

    for (line in markdown.lines()) {
        if (line.trim() == "```") {
            if (inCode) {
                blocks.add(Block.Code(code.toString().trimEnd()))
                code.setLength(0)
                inCode = false
            } else {
                flushPara()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.appendLine(line)
            continue
        }
        when {
            line.isBlank() -> flushPara()
            line.startsWith("### ") -> { flushPara(); blocks.add(Block.Heading(3, line.removePrefix("### "))) }
            line.startsWith("## ") -> { flushPara(); blocks.add(Block.Heading(2, line.removePrefix("## "))) }
            line.startsWith("# ") -> { flushPara(); blocks.add(Block.Heading(1, line.removePrefix("# "))) }
            line.startsWith("- ") || line.startsWith("* ") -> { flushPara(); blocks.add(Block.Bullet(line.drop(2))) }
            line.trim() == "---" -> { flushPara(); blocks.add(Block.Divider) }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(line.trim())
            }
        }
    }
    flushPara()
    if (inCode && code.isNotBlank()) blocks.add(Block.Code(code.toString().trimEnd()))
    return blocks
}

/** Renders inline **bold**, `code`, and [text](url). */
private fun inline(text: String, linkColor: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append("**"); i += 2
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '[' -> {
                val close = text.indexOf(']', i)
                if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                    val urlEnd = text.indexOf(')', close + 2)
                    if (urlEnd > close) {
                        val label = text.substring(i + 1, close)
                        val url = text.substring(close + 2, urlEnd)
                        withLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                            ),
                        ) { append(label) }
                        i = urlEnd + 1
                    } else {
                        append(text[i]); i++
                    }
                } else {
                    append(text[i]); i++
                }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
