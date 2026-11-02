package com.tooheavy.app.ui.running

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tooheavy.app.data.ActiveTrack
import com.tooheavy.app.data.MAX_ACCURACY_M
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.RunTracker
import com.tooheavy.app.data.formatClock
import com.tooheavy.app.data.formatDistanceValue
import com.tooheavy.app.data.paceText
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.Warmup
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun hasPreciseLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Whether Android may restrict TooHeavy in the background to save battery (it isn't exempt). */
fun batteryRestricted(context: Context): Boolean =
    runCatching { !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
        .getOrDefault(false)

/** Asks Android to leave TooHeavy out of battery optimisation, or opens the list where that is set. */
fun openBatterySettings(context: Context) {
    val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    for (intent in listOf(ask, list)) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

private fun gpsOn(context: Context): Boolean =
    runCatching { context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

/**
 * "Record with GPS" for a distance exercise: asks for precise location (and notifications, for the recording
 * notification) the first time, checks location is switched on, then calls [onStart].
 */
@Composable
fun RecordRunButton(autoStart: Boolean = false, onAutoStartHandled: () -> Unit = {}, onStart: () -> Unit) {
    val context = LocalContext.current
    var problem by remember { mutableStateOf<String?>(null) }
    var batteryDialog by remember { mutableStateOf(false) }
    // The first time, explains battery optimisation before recording; after that just records.
    fun begin() {
        if (batteryRestricted(context) && !Repo.settings.batteryPromptShown) batteryDialog = true else onStart()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        when {
            result[Manifest.permission.ACCESS_FINE_LOCATION] != true ->
                problem = "Recording needs precise location. Allow it for TooHeavy in the system settings."
            !gpsOn(context) -> problem = "Location is switched off."
            else -> {
                problem = null
                begin()
            }
        }
    }
    fun tryStart() {
        when {
            !hasPreciseLocation(context) -> {
                val perms = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                launcher.launch(perms.toTypedArray())
            }
            !gpsOn(context) -> problem = "Location is switched off."
            else -> {
                problem = null
                begin()
            }
        }
    }
    // "Start a run" opens the workout ready to go: recording starts as if the button had been tapped.
    LaunchedEffect(autoStart) {
        if (autoStart) {
            onAutoStartHandled()
            tryStart()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { tryStart() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Record with GPS")
        }
        problem?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p, style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val intent = if (p.startsWith("Location is"))
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    else
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Settings") }
            }
        }
    }

    if (batteryDialog) {
        fun answer(allow: Boolean) {
            batteryDialog = false
            Repo.updateSettings(Repo.settings.copy(batteryPromptShown = true))
            onStart()
            if (allow) openBatterySettings(context)
        }
        AlertDialog(
            onDismissRequest = { answer(false) },
            title = { Text("Keep recording with the screen off") },
            text = {
                Text(
                    "To save battery, Android can stop apps in the background, which would cut a run short. Allow " +
                        "TooHeavy to run without battery restrictions? It only uses GPS while a run is recorded.\n\n" +
                        "On Samsung, Xiaomi and some other phones, also set TooHeavy's battery use to \"Unrestricted\" " +
                        "in its app settings.",
                )
            },
            confirmButton = { TextButton(onClick = { answer(true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { answer(false) }) { Text("Not now") } },
        )
    }
}

/** The run being recorded: map, distance, time and pace, with pause/resume, finish and discard. */
@Composable
fun LiveRunPanel(track: ActiveTrack, unit: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(track.resumedAt) {
        now = System.currentTimeMillis()
        while (track.resumedAt != null) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    var confirmDiscard by remember { mutableStateOf(false) }
    val accuracy = track.lastAccuracy
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(
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
                        accuracy > MAX_ACCURACY_M -> "Weak GPS signal (±${accuracy.roundToInt()} m)"
                        else -> "Recording · GPS ±${accuracy.roundToInt()} m"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("Auto-pause", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Switch(checked = Repo.settings.autoPauseRuns, onCheckedChange = { RunTracker.setAutoPause(it) })
            }
            if (batteryRestricted(context)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Battery optimisation is on: the recording may stop with the screen off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Warmup,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { openBatterySettings(context) }) { Text("Fix") }
                }
            }
            if (track.points.isNotEmpty()) {
                RouteMap(track.points, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)), follow = true)
            }
            Row(Modifier.fillMaxWidth()) {
                LiveStat(formatDistanceValue(track.distanceKm, unit), unit, Modifier.weight(1f))
                LiveStat(formatClock(track.movingMs(now)), "time", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                LiveStat(paceText(track.paceSecPerKm(now), unit), "avg /$unit", Modifier.weight(1f))
                LiveStat(paceText(if (track.paused) null else track.currentPaceSecPerKm(), unit), "now /$unit", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { if (track.paused) RunTracker.resume() else RunTracker.pause() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(if (track.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (track.paused) "Resume" else "Pause")
                }
                Button(onClick = { RunTracker.finish() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Finish")
                }
            }
            TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Discard recording", color = Danger)
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this recording?") },
            text = { Text("The route, distance and time recorded so far will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    RunTracker.discard()
                }) { Text("Discard", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep recording") } },
        )
    }
}

@Composable
private fun LiveStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AccentBright)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
