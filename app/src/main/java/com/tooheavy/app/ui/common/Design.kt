package com.tooheavy.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tooheavy.app.R
import com.tooheavy.app.ui.theme.Accent
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Hairline
import com.tooheavy.app.ui.theme.Ink
import com.tooheavy.app.ui.theme.NumberFont
import com.tooheavy.app.ui.theme.OnAccent
import com.tooheavy.app.ui.theme.Overline
import com.tooheavy.app.ui.theme.Surface1
import com.tooheavy.app.ui.theme.Surface3
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.TextPrimary
import com.tooheavy.app.ui.theme.TextSecondary

val CardShape = RoundedCornerShape(18.dp)

/** Ink background with soft violet and ember glows near the top; sits behind every screen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Ink)
            .drawBehind {
                // A faint warm light from above, like a lamp over dark wood.
                val w = size.width
                drawCircle(
                    Brush.radialGradient(
                        listOf(Accent.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(w * 0.5f, -w * 0.35f),
                        radius = w * 1.05f,
                    ),
                    radius = w * 1.05f,
                    center = Offset(w * 0.5f, -w * 0.35f),
                )
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary) { content() }
    }
}

/**
 * The app's card: a flat dark panel with a hairline border. Mirrors material3 Card's parameters, so
 * `colors = CardDefaults.cardColors(containerColor = primaryContainer)` turns it into a gold-edged highlight card.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    colors: CardColors? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val highlight = colors?.containerColor == MaterialTheme.colorScheme.primaryContainer
    val fill = if (highlight) Brush.verticalGradient(listOf(Color(0xFF221E17), Color(0xFF181612))) else SolidColor(Surface1)
    val edge = if (highlight) Accent.copy(alpha = 0.45f) else Hairline
    CompositionLocalProvider(LocalContentColor provides TextPrimary) {
        Column(
            modifier
                .clip(shape)
                .background(fill)
                .border(1.dp, edge, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            content = content,
        )
    }
}

/** Transparent top bar over the app backdrop. Same parameters as material3 TopAppBar; [colors] is ignored. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    @Suppress("UNUSED_PARAMETER") colors: TopAppBarColors? = null,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

/** Big title for top-level tabs: a spaced gold overline (e.g. the date) above a serif headline. */
@Composable
fun ScreenHeader(
    title: String,
    overline: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(overline.uppercase(), style = Overline, color = Accent.copy(alpha = 0.85f))
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Section label with an optional trailing action (e.g. a TextButton). */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Spaced uppercase card label with an optional gold icon, e.g. "THIS WEEK". */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = Overline, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Big number with a small unit, e.g. "12,400 kg". */
@Composable
fun BigNumber(
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    size: Int = 30,
    brush: Brush? = null,
    color: Color = TextPrimary,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        val style = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Normal, fontSize = size.sp, letterSpacing = (-0.3).sp)
        Text(value, style = if (brush != null) style.copy(brush = brush) else style.copy(color = color), maxLines = 1)
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = (size / 7).dp),
                maxLines = 1,
            )
        }
    }
}

/** Glass tile with a label, a big number and an optional footnote. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    footnote: String? = null,
) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            CardLabel(label, icon = icon)
            Spacer(Modifier.height(8.dp))
            BigNumber(value, unit, size = 26)
            if (footnote != null) {
                Text(footnote, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Gold line icon inside a hairline circle. */
@Composable
fun IconBubble(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Accent.copy(alpha = 0.06f))
            .border(1.dp, Accent.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(size * 0.5f))
    }
}

/** Primary call to action: brushed gold with dark text. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    CompositionLocalProvider(LocalContentColor provides OnAccent) {
        Row(
            modifier
                .height(height)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFFDCC495), Accent)))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            ) { content() }
        }
    }
}

/** Small rounded label, e.g. "DELOAD" or "+2.5 kg". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .clip(CircleShape)
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color.copy(alpha = 0.55f), CircleShape))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
            color = if (filled) OnAccent else color,
            maxLines = 1,
        )
    }
}

/** Segmented control; the selected segment is lifted and set in gold. */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(Surface1)
            .border(1.dp, Hairline, shape)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (active) Modifier.background(Surface3).border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) AccentBright else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The app icon (gold barbell in a hairline ring on charcoal), drawn from the launcher vectors. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f))) {
        // The launcher layers are 108 units with the visible area in the middle 72.
        val bleed = size * (108f / 72f)
        Box(Modifier.requiredSize(bleed).align(Alignment.Center)) {
            Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "TooHeavy", modifier = Modifier.fillMaxSize())
        }
    }
}

/** Placeholder shown inside a chart card when there is nothing to plot yet. */
@Composable
fun EmptyChartNote(text: String, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}
