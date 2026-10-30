package com.tooheavy.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tooheavy.app.BuildConfig
import com.tooheavy.app.data.Changelog
import com.tooheavy.app.data.Updater
import com.tooheavy.app.data.WhatsNew
import com.tooheavy.app.ui.theme.Accent
import java.text.SimpleDateFormat
import java.util.Locale

/** "2026-10-30" -> "Oct 30, 2026" */
private fun releaseDate(date: String): String =
    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull()
        ?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(it) } ?: date

/** App-wide: the release notes once after an update (waits while the update dialog is open). */
@Composable
fun WhatsNewDialog() {
    if (!WhatsNew.showDialog || Updater.showDialog) return
    ChangelogDialog(onDismiss = { WhatsNew.showDialog = false })
}

/** Release notes for this version first, then older ones. */
@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    val current = BuildConfig.VERSION_NAME
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (Changelog.forVersion(current) != null) "What's new in $current" else "What's new") },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(Changelog.entries, key = { it.version }) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            buildString {
                                append("Version ${entry.version}")
                                if (entry.version == current) append(" (installed)")
                                append(" · ").append(releaseDate(entry.date))
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.version == current) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        entry.changes.forEach {
                            Text(
                                "•  $it",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
