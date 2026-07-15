package com.cardmanager.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

@Composable
fun PredictiveBackPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val progress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler { events ->
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            onBack()
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                progress.animateTo(0f, tween(durationMillis = 140))
            }
        }
    }

    Box(
        modifier.graphicsLayer {
            val amount = progress.value
            val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
            translationX = size.width * 0.08f * amount * direction
            scaleX = 1f - 0.018f * amount
            scaleY = 1f - 0.018f * amount
            alpha = 1f - 0.05f * amount
            transformOrigin = TransformOrigin(
                pivotFractionX = if (direction > 0f) 0f else 1f,
                pivotFractionY = 0.5f
            )
        },
        content = content
    )
}
