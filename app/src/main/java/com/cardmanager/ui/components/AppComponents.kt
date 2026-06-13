package com.cardmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
val PanelShape = RoundedCornerShape(20.dp)
val ItemShape = RoundedCornerShape(16.dp)
val ChipShape = RoundedCornerShape(999.dp)

fun screenPaddingFor(width: Dp): PaddingValues = PaddingValues(
    horizontal = when {
        width >= 840.dp -> 28.dp
        width >= 600.dp -> 22.dp
        else -> 16.dp
    },
    vertical = 14.dp
)

fun Modifier.responsiveDialogWidth(maxWidth: Dp = 560.dp): Modifier =
    fillMaxWidth(0.94f).widthIn(max = maxWidth)

@Composable
fun AppPanel(
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = cs.surface,
        tonalElevation = tonalElevation,
        shadowElevation = 0.dp
    ) {
        Box(
            Modifier
                .border(1.dp, cs.outline.copy(alpha = 0.18f), PanelShape)
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(ItemShape)
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.18f), ItemShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
            }
            Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cs.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(25.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(body, fontSize = 12.sp, color = cs.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(ChipShape)
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

fun LazyListScope.bottomSpacer(height: Dp = 88.dp) {
    item { Spacer(Modifier.height(height)) }
}
