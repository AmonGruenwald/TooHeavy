package com.tooheavy.app.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tooheavy.app.data.ActiveTrack
import com.tooheavy.app.data.MAX_ACCURACY_M
import com.tooheavy.app.data.RunTracker
import com.tooheavy.app.data.formatClock
import com.tooheavy.app.data.formatDistanceValue
import com.tooheavy.app.data.paceText
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Ink
import com.tooheavy.app.ui.theme.OnAccent
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.Warmup
import kotlinx.coroutines.delay

/**
 * What a locked phone shows during a run: the route on a map that follows you, distance and moving time large,
 * average and current pace, and Pause / Resume. Nothing else of the app is reachable until the phone is unlocked
 * ([onUnlock] asks the system to unlock). Finishing and discarding need the unlocked app, so a tap in a pocket
 * can't end the run.
 */
@Composable
fun LockRunScreen(track: ActiveTrack, unit: String, onUnlock: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(track.resumedAt) {
        now = System.currentTimeMillis()
        while (track.resumedAt != null) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    // Back mustn't reach the app underneath while locked.
    BackHandler {}
    val accuracy = track.lastAccuracy
    val manualPause = track.paused && !track.autoPaused

    // A Surface takes all touches, so nothing behind it can be tapped.
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(
                        when {
                            track.paused -> Warmup
                            accuracy == null || accuracy > MAX_ACCURACY_M -> Danger
                            else -> Success
                        },
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        track.autoPaused -> "Auto-paused · carries on when you move"
                        track.paused -> "Paused"
                        accuracy == null -> "Waiting for GPS…"
                        accuracy > MAX_ACCURACY_M -> "Weak GPS signal"
                        else -> "Recording"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The route, as large as the screen allows.
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (track.points.isNotEmpty()) {
                    RouteMap(track.points, Modifier.fillMaxSize(), follow = true)
                } else {
                    Text("Waiting for GPS…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }

            Row(Modifier.fillMaxWidth()) {
                BigStat(formatDistanceValue(track.distanceKm, unit), unit, Modifier.weight(1f))
                BigStat(formatClock(track.movingMs(now)), "time", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                SmallStat(paceText(track.paceSecPerKm(now), unit), "avg pace /$unit", Modifier.weight(1f))
                SmallStat(paceText(if (track.paused) null else track.currentPaceSecPerKm(), unit), "pace now /$unit", Modifier.weight(1f))
            }

            Button(
                onClick = { if (track.paused) RunTracker.resume() else RunTracker.pause() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manualPause) Success else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (manualPause) OnAccent else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(if (track.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (track.paused) "Resume" else "Pause", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onUnlock, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextMuted)
                Spacer(Modifier.width(6.dp))
                Text("Unlock to finish or open the app", color = TextMuted)
            }
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = AccentBright, textAlign = TextAlign.Center, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}

@Composable
private fun SmallStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}
