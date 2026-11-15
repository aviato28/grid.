package com.nbansal.grid.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.JetBrainsMono
import com.nbansal.grid.ui.theme.MonoLabel
import kotlin.math.roundToInt

/** Turns a GitHub release body into plain bullet lines. */
internal fun changelogLines(body: String): List<String> =
    body.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.removePrefix("-").removePrefix("*").removePrefix("•").trim() }
        .filter { it.isNotEmpty() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(state: UpdateState, onInstall: () -> Unit, onRetryInstall: () -> Unit, onDismiss: () -> Unit) {
    val visible = state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.ReadyToInstall
    if (!visible) return
    val accent = GridTheme.colors.accent
    val muted = GridTheme.colors.muted

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            val version = (state as? UpdateState.Available)?.version
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Update available · ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (version != null) {
                    Text("v$version", fontFamily = JetBrainsMono, fontSize = 16.sp, color = accent)
                }
            }
            if (state is UpdateState.Available) {
                Spacer(Modifier.height(12.dp))
                changelogLines(state.notes).take(8).forEach { line ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("—  ", color = muted, fontFamily = JetBrainsMono, fontSize = 13.sp)
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            when (state) {
                is UpdateState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = accent,
                        trackColor = GridTheme.colors.line,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Downloading… ${(state.progress * 100).roundToInt()}%", style = MonoLabel, color = muted)
                }
                is UpdateState.ReadyToInstall ->
                    SheetButton("Install", onRetryInstall)
                else -> SheetButton("Download & install", onInstall)
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = GridTheme.colors.accent,
            contentColor = androidx.compose.ui.graphics.Color.White,
        ),
    ) { Text(text, fontWeight = FontWeight.Medium) }
}
