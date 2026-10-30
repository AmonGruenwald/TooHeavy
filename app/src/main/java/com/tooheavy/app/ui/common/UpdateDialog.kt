package com.tooheavy.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tooheavy.app.BuildConfig
import com.tooheavy.app.data.Updater
import com.tooheavy.app.data.Updater.State

/** App-wide dialog for [Updater]; shown whenever Updater.showDialog is set. */
@Composable
fun UpdateDialog() {
    if (!Updater.showDialog) return
    val context = LocalContext.current
    val later: @Composable () -> Unit = { TextButton(onClick = Updater::dismiss) { Text("Later") } }
    val ok: @Composable () -> Unit = { TextButton(onClick = Updater::dismiss) { Text("OK") } }

    when (val s = Updater.state) {
        State.Idle, State.Checking -> AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("Checking for updates") },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = Updater::dismiss) { Text("Hide") } },
        )
        State.UpToDate -> AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("You're up to date") },
            text = { Text("TooHeavy ${BuildConfig.VERSION_NAME} is the latest version.") },
            confirmButton = ok,
        )
        is State.Available -> AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("Update available") },
            text = {
                Text(
                    "TooHeavy ${s.release.version} is available. You're on a metered connection; " +
                        "the download is ${s.release.size / 1_000_000} MB.",
                )
            },
            confirmButton = { TextButton(onClick = Updater::download) { Text("Download") } },
            dismissButton = later,
        )
        is State.Downloading -> AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("Downloading TooHeavy ${s.release.version}") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(s.progress * 100).toInt()} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = Updater::dismiss) { Text("Hide") } },
        )
        is State.Ready -> AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("Update ready") },
            text = { Text("TooHeavy ${s.release.version} has been downloaded. Install it now? Your data is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    Updater.dismiss()
                    Updater.install(context)
                }) { Text("Install") }
            },
            dismissButton = later,
        )
        is State.Failed -> AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("Update failed") },
            text = { Text(s.message) },
            confirmButton = ok,
        )
    }
}
