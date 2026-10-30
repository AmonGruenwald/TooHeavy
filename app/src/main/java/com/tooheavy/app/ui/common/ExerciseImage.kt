package com.tooheavy.app.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tooheavy.app.data.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private object AssetImageCache {
    val cache = LruCache<String, ImageBitmap>(120)

    fun load(context: Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        return runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()?.also { cache.put(path, it) }
    }
}

/** Image from the app's assets folder (e.g. Exercise.images[0]). Shows a neutral placeholder while loading or if missing. */
@Composable
fun AssetImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = path?.let { AssetImageCache.cache.get(it) }, path) {
        if (path != null && value == null) value = withContext(Dispatchers.IO) { AssetImageCache.load(context, path) }
    }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        } else if (path == null) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color(0xFF9E9E9E))
        }
    }
}

/** Small rounded thumbnail of an exercise's start position, for list rows. */
@Composable
fun ExerciseThumbnail(exercise: Exercise?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    AssetImage(
        path = exercise?.images?.firstOrNull(),
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 4)),
    )
}

/**
 * Visual instruction: loops between the start (images[0]) and end (images[1]) position photos with a crossfade.
 * Full width, 3:2 aspect ratio by default.
 */
@Composable
fun ExerciseAnimation(exercise: Exercise, modifier: Modifier = Modifier, frameMs: Long = 1100) {
    var frame by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    val frames = exercise.images
    LaunchedEffect(exercise.id) {
        if (frames.size > 1) while (true) {
            delay(frameMs)
            frame = (frame + 1) % frames.size
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (frames.isEmpty()) {
            AssetImage(null, Modifier.fillMaxSize())
        } else {
            Crossfade(targetState = frame.coerceIn(0, frames.lastIndex), animationSpec = tween(350), label = "exercise-frame") { i ->
                AssetImage(frames[i], Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}
