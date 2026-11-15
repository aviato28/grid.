package com.nbansal.grid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.grid.BuildConfig
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel
import com.nbansal.grid.ui.theme.SpaceGrotesk

@Composable
fun HomeScreen(checking: Boolean, onNewSheet: () -> Unit, onCheckForUpdates: () -> Unit) {
    val colors = GridTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("Grid")
                withStyle(SpanStyle(color = colors.accent)) { append(".") }
            },
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNewSheet,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
        ) { Text("New sheet", fontWeight = FontWeight.Medium) }

        Spacer(Modifier.height(32.dp))
        Text("RECENT", style = MonoLabel, color = colors.muted)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.line)
        Text("No sheets yet. Start one above.", color = colors.muted, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = colors.line)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("v${BuildConfig.VERSION_NAME}", style = MonoLabel, color = colors.muted)
            TextButton(onClick = onCheckForUpdates, enabled = !checking) {
                Text(if (checking) "Checking…" else "Check for updates", color = colors.accent)
            }
        }
    }
}
