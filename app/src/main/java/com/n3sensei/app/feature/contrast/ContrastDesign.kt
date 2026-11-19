package com.n3sensei.app.feature.contrast

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.n3sensei.app.core.designsystem.theme.LocalReduceMotion
import com.n3sensei.app.core.designsystem.theme.N3Motion
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.domain.contrast.ContrastContentType

// =============================================================================================
// Contrast Lab design system (UI redesign U1–U2). Every size is in sp, so the system font scale
// applies; screens use these tokens instead of ad-hoc sizes. Japanese styles are bigger than the
// Vietnamese explanations around them, with roomy line heights for furigana and stacked diacritics.
// =============================================================================================

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

object ContrastText {
    /** Screen and hero titles. */
    val screenTitle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, lineHeightStyle = Trim)

    /** Title of a contrast group (Vietnamese, may contain Japanese). */
    val groupTitle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = Trim).japanese()

    /** Section headings inside a screen. */
    val section = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)

    /** A single kanji being compared. */
    val heroKanji = TextStyle(fontSize = 52.sp, lineHeight = 64.sp, fontWeight = FontWeight.Medium).japanese()

    /** A grammar pattern or a word being compared. */
    val heroPattern = TextStyle(fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold).japanese()

    /** Item labels in list cards ("ことにしている vs ことになっている"). */
    val itemLabel = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold).japanese()

    /** Japanese example sentences and question prompts. */
    val exampleJp = TextStyle(fontSize = 22.sp, lineHeight = 36.sp).japanese()

    /** Vietnamese explanations. */
    val body = TextStyle(fontSize = 17.sp, lineHeight = 26.sp)

    /** Secondary Vietnamese text (translations, notes). */
    val bodySecondary = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)

    /** Answer options. */
    val answer = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium).japanese()

    /** An answer option that is a single kanji or a short kanji word. */
    val answerKanji = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium).japanese()

    /** Question instructions. */
    val question = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)

    /** Small labels: badges, field names, counters. */
    val label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)

    /** Captions and hints. */
    val caption = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)

    /** Big figures on dashboards. */
    val figure = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
}

/** One colour role: accent (text, icons, borders), container (tinted background), text on it, and a header gradient. */
@Immutable
data class Tone(val accent: Color, val container: Color, val onContainer: Color, val gradient: List<Color>)

/**
 * Category and state colours, light and dark (all pairs checked for WCAG AA ≥ 4.5:1, including
 * white text on the gradients). A screen uses one main tone and at most two supporting ones.
 */
object ContrastTones {
    private val Light = mapOf(
        "grammar" to Tone(Color(0xFF4338CA), Color(0xFFEEF2FF), Color(0xFF312E81), listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))),
        "vocab" to Tone(Color(0xFF0F766E), Color(0xFFE7F8F3), Color(0xFF134E4A), listOf(Color(0xFF0F766E), Color(0xFF047857))),
        "kanji" to Tone(Color(0xFFB45309), Color(0xFFFFF4E5), Color(0xFF7C2D12), listOf(Color(0xFFB45309), Color(0xFFC2410C))),
        "practice" to Tone(Color(0xFF1D4ED8), Color(0xFFEFF6FF), Color(0xFF1E3A8A), listOf(Color(0xFF1D4ED8), Color(0xFF6D28D9))),
        "correct" to Tone(Color(0xFF15803D), Color(0xFFECFDF3), Color(0xFF14532D), listOf(Color(0xFF15803D), Color(0xFF0F766E))),
        "incorrect" to Tone(Color(0xFFBE123C), Color(0xFFFFF1F2), Color(0xFF881337), listOf(Color(0xFFBE123C), Color(0xFFBE123C))),
        "review" to Tone(Color(0xFF0E7490), Color(0xFFE8FBFD), Color(0xFF164E63), listOf(Color(0xFF0E7490), Color(0xFF4338CA))),
    )
    private val Dark = mapOf(
        "grammar" to Tone(Color(0xFFA5B4FC), Color(0xFF1F1D47), Color(0xFFE0E7FF), listOf(Color(0xFF3730A3), Color(0xFF5B21B6))),
        "vocab" to Tone(Color(0xFF5EEAD4), Color(0xFF0B2E2B), Color(0xFFCCFBF1), listOf(Color(0xFF115E59), Color(0xFF065F46))),
        "kanji" to Tone(Color(0xFFFCD34D), Color(0xFF3A2410), Color(0xFFFEF3C7), listOf(Color(0xFF92400E), Color(0xFF9A3412))),
        "practice" to Tone(Color(0xFF93C5FD), Color(0xFF16244A), Color(0xFFDBEAFE), listOf(Color(0xFF1E40AF), Color(0xFF5B21B6))),
        "correct" to Tone(Color(0xFF86EFAC), Color(0xFF0C2A18), Color(0xFFDCFCE7), listOf(Color(0xFF166534), Color(0xFF115E59))),
        "incorrect" to Tone(Color(0xFFFDA4AF), Color(0xFF3B0F1A), Color(0xFFFFE4E6), listOf(Color(0xFF9F1239), Color(0xFF9F1239))),
        "review" to Tone(Color(0xFF67E8F9), Color(0xFF0B2B36), Color(0xFFCFFAFE), listOf(Color(0xFF155E75), Color(0xFF3730A3))),
    )

    /** The items of one group get different hues so A and B never look alike. */
    private val ItemsLight = listOf(
        Tone(Color(0xFF4338CA), Color(0xFFEEF2FF), Color(0xFF312E81), emptyList()),
        Tone(Color(0xFFA21CAF), Color(0xFFFDF2FD), Color(0xFF701A75), emptyList()),
        Tone(Color(0xFF0369A1), Color(0xFFEAF6FD), Color(0xFF0C4A6E), emptyList()),
        Tone(Color(0xFFC2410C), Color(0xFFFFF3EB), Color(0xFF7C2D12), emptyList()),
    )
    private val ItemsDark = listOf(
        Tone(Color(0xFFA5B4FC), Color(0xFF1F1D47), Color(0xFFE0E7FF), emptyList()),
        Tone(Color(0xFFF0ABFC), Color(0xFF36123A), Color(0xFFFAE8FF), emptyList()),
        Tone(Color(0xFF7DD3FC), Color(0xFF0C2A3F), Color(0xFFE0F2FE), emptyList()),
        Tone(Color(0xFFFDBA74), Color(0xFF3A1C0C), Color(0xFFFFEDD5), emptyList()),
    )

    @Composable
    fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    @Composable
    private fun pick(key: String): Tone = (if (isDark()) Dark else Light).getValue(key)

    @Composable
    fun category(type: ContrastContentType): Tone = pick(
        when (type) {
            ContrastContentType.GRAMMAR -> "grammar"
            ContrastContentType.VOCABULARY -> "vocab"
            ContrastContentType.KANJI -> "kanji"
        },
    )

    @Composable fun practice(): Tone = pick("practice")
    @Composable fun correct(): Tone = pick("correct")
    @Composable fun incorrect(): Tone = pick("incorrect")
    @Composable fun review(): Tone = pick("review")

    @Composable
    fun neutral(): Tone {
        val c = MaterialTheme.colorScheme
        return Tone(c.onSurfaceVariant, c.surfaceContainerHigh, c.onSurface, listOf(c.surfaceContainerHigh, c.surfaceContainerHighest))
    }

    @Composable
    fun item(index: Int): Tone = (if (isDark()) ItemsDark else ItemsLight)[index.coerceAtLeast(0) % 4]
}

/** The short Japanese glyph that marks a category (vector-free, no download). */
fun categoryGlyph(type: ContrastContentType): String = when (type) {
    ContrastContentType.GRAMMAR -> "文"
    ContrastContentType.VOCABULARY -> "語"
    ContrastContentType.KANJI -> "漢"
}

val CardShape = RoundedCornerShape(20.dp)
val HeroShape = RoundedCornerShape(24.dp)
val ChipShape = RoundedCornerShape(50)

// ---- Components ----

/**
 * The Contrast card: soft corners, hairline border, tinted with [tone] when given. Clickable cards
 * shrink very slightly while pressed (not when motion is reduced).
 */
@Composable
fun ContrastCard(
    modifier: Modifier = Modifier,
    tone: Tone? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed && onClick != null && !reduce) 0.98f else 1f, animationSpec = spring(stiffness = 600f), label = "cardPress")
    val container = tone?.container ?: colors.surfaceContainerLow
    val border = (tone?.accent ?: colors.outlineVariant).copy(alpha = if (tone != null) 0.28f else 1f)
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
    }
    // The click (and its semantics) belongs to the card itself, so the whole card is one button for TalkBack and tests.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().scale(scale),
            shape = CardShape,
            color = container,
            contentColor = tone?.onContainer ?: colors.onSurface,
            border = BorderStroke(1.dp, border),
            shadowElevation = if (tone == null) 0.dp else 1.dp,
            interactionSource = interaction,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = CardShape,
            color = container,
            contentColor = tone?.onContainer ?: colors.onSurface,
            border = BorderStroke(1.dp, border),
            shadowElevation = if (tone == null) 0.dp else 1.dp,
            content = body,
        )
    }
}

/** A hero block with a soft gradient and a quiet wave pattern (seigaiha arcs) in one corner. White content. */
@Composable
fun GradientHeader(tone: Tone, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clip(HeroShape).background(Brush.linearGradient(tone.gradient))) {
        WavePattern(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) { content() }
        }
    }
}

@Composable
private fun WavePattern(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.42f
        val stroke = Stroke(width = 2.dp.toPx())
        val color = Color.White.copy(alpha = 0.12f)
        val cx = size.width - r * 0.35f
        val cy = size.height + r * 0.15f
        for (i in 0 until 4) {
            val rr = r * (1f - i * 0.22f)
            drawArc(color, 180f, 180f, useCenter = false, topLeft = Offset(cx - rr, cy - rr), size = Size(rr * 2, rr * 2), style = stroke)
            drawArc(color, 180f, 180f, useCenter = false, topLeft = Offset(cx - rr - r * 1.1f, cy - rr), size = Size(rr * 2, rr * 2), style = stroke)
        }
    }
}

/** Category pill: glyph + name, in the category colour. */
@Composable
fun CategoryBadge(type: ContrastContentType, modifier: Modifier = Modifier, onGradient: Boolean = false) {
    val tone = ContrastTones.category(type)
    val bg = if (onGradient) Color.White.copy(alpha = 0.2f) else tone.container
    val fg = if (onGradient) Color.White else tone.accent
    Row(
        modifier.clip(ChipShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(categoryGlyph(type), style = ContrastText.label.japanese(), color = fg)
        Text(typeLabel(type), style = ContrastText.label, color = fg)
    }
}

/** Small coloured pill (status, level, counts). */
@Composable
fun TonePill(text: String, tone: Tone, modifier: Modifier = Modifier, onGradient: Boolean = false) {
    val bg = if (onGradient) Color.White.copy(alpha = 0.2f) else tone.container
    val fg = if (onGradient) Color.White else tone.accent
    Text(text, style = ContrastText.label, color = fg, modifier = modifier.clip(ChipShape).background(bg).border(1.dp, fg.copy(alpha = 0.25f), ChipShape).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Section heading with a coloured marker. */
@Composable
fun SectionTitle(text: String, tone: Tone, modifier: Modifier = Modifier, glyph: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (glyph != null) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tone.container), contentAlignment = Alignment.Center) {
                Text(glyph, style = ContrastText.label.copy(fontSize = 16.sp).japanese(), color = tone.accent)
            }
        } else {
            Box(Modifier.width(4.dp).heightIn(min = 22.dp).clip(RoundedCornerShape(2.dp)).background(tone.accent))
        }
        Text(text, style = ContrastText.section, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
    }
}

/** A circular progress ring drawn with Canvas; [progress] 0..1. Animated unless motion is reduced. */
@Composable
fun ProgressRing(progress: Float, tone: Tone, modifier: Modifier = Modifier, size: Dp = 72.dp, stroke: Dp = 8.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = if (reduce) snap() else tween(700, easing = N3Motion.EnterEasing), label = "ring")
    val track = tone.accent.copy(alpha = 0.16f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, -90f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (animated > 0f) drawArc(tone.accent, -90f, 360f * animated, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A horizontal bar in a tone (category progress, error frequency). */
@Composable
fun ToneBar(fraction: Float, tone: Tone, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = if (reduce) snap() else tween(600, easing = N3Motion.EnterEasing), label = "bar")
    Box(modifier.fillMaxWidth().heightIn(min = 10.dp).clip(ChipShape).background(tone.accent.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(animated).heightIn(min = 10.dp).clip(ChipShape).background(tone.accent))
    }
}

/**
 * A card whose body opens and closes (progressive disclosure of extra material). The size change
 * is animated, or instant when motion is reduced; the state survives configuration changes.
 */
@Composable
fun ExpandableCard(
    title: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val reduce = LocalReduceMotion.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, animationSpec = if (reduce) snap() else tween(N3Motion.MEDIUM), label = "chevron")
    val expandedLabel = androidx.compose.ui.res.stringResource(if (expanded) com.n3sensei.app.R.string.contrast_ui_expanded else com.n3sensei.app.R.string.contrast_ui_collapsed)
    ContrastCard(
        modifier = modifier.animateContentSize(if (reduce) snap() else tween(N3Motion.LONG, easing = N3Motion.StandardEasing)),
        tone = null,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = expandedLabel }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tone.accent))
            Column(Modifier.weight(1f)) {
                Text(title, style = ContrastText.section.copy(fontSize = 18.sp, lineHeight = 26.sp), modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = tone.accent, modifier = Modifier.rotate(rotation))
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** The main action of a screen: a large gradient button (min 56 dp). */
@Composable
fun GradientButton(
    text: String,
    tone: Tone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed && !reduce) 0.97f else 1f, animationSpec = spring(stiffness = 700f), label = "buttonPress")
    val background = if (enabled) Brush.horizontalGradient(tone.gradient) else Brush.horizontalGradient(listOf(colors.surfaceContainerHighest, colors.surfaceContainerHighest))
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(enabled = enabled, interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (leading != null) androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) { leading() }
            Text(text, style = ContrastText.question.copy(fontSize = 18.sp), color = if (enabled) Color.White else colors.onSurfaceVariant)
        }
    }
}

/** A compact figure tile: number on top, label below, in a tone. One accessibility node "label: value". */
@Composable
fun FigureTile(value: String, label: String, tone: Tone, modifier: Modifier = Modifier, description: String = "$label: $value") {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tone.container)
            .border(1.dp, tone.accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = ContrastText.figure, color = tone.accent)
        Text(label, style = ContrastText.caption, color = tone.onContainer)
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.heightIn(min = height))
