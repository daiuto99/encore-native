package com.encore.feature.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.encore.core.ui.theme.LocalEncoreColors

/**
 * Decision Gate dialog — shown when both local and remote markdownBody diverged
 * since the last sync ([ContentSyncStatus.Conflict]).
 *
 * Dimensions: 400dp width, centred, RoundedCornerShape(12dp) — per Zen UI spec.
 * Hit targets: "Keep Local" / "Keep Remote" buttons are 60dp height.
 *
 * Layout:
 *  - Header: song title + conflict explanation
 *  - Two-column diff preview: Local (green tint) | Remote (red tint)
 *  - Action row: Keep Local | Keep Remote (60dp)
 *  - Cancel link
 *
 * The diff preview shows raw markdown bodies for now. A line-level diff renderer
 * will replace this in a future sprint once the Ktor client is live and we have
 * real remote content to diff against.
 *
 * @param songTitle      Display title for the conflicted song
 * @param localBody      Current local markdownBody
 * @param remoteBody     Server's markdownBody (fetched during sync)
 * @param onKeepLocal    Called when user chooses to keep the local version
 * @param onKeepRemote   Called when user chooses to accept the remote version
 * @param onDismiss      Called when user taps Cancel (no change committed)
 */
@Composable
fun ConflictResolutionDialog(
    songTitle: String,
    localBody: String,
    remoteBody: String,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    onDismiss: () -> Unit
) {
    val encoreColors = LocalEncoreColors.current

    // SetColor pastels repurposed: green tint for local (additions), red tint for remote (changes)
    val localTint  = Color(0xFF4CAF50).copy(alpha = 0.08f)
    val remoteTint = Color(0xFFFF3B30).copy(alpha = 0.08f)
    val localBorder  = Color(0xFF4CAF50).copy(alpha = 0.30f)
    val remoteBorder = Color(0xFFFF3B30).copy(alpha = 0.30f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = encoreColors.cardBackground,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // ── Header ───────────────────────────────────────────────────
                Text(
                    text = "Sync Conflict",
                    color = Color(0xFFFF3B30),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = songTitle,
                    color = encoreColors.titleText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "This song was edited on both the tablet and the server since the last sync. Choose which version to keep.",
                    color = encoreColors.artistText,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(20.dp))

                // ── Diff preview: two columns ─────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Local column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LOCAL",
                            color = Color(0xFF4CAF50),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(localTint, RoundedCornerShape(8.dp))
                                .border(1.dp, localBorder, RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            Text(
                                text = localBody.trimEnd(),
                                color = encoreColors.titleText.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Remote column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SERVER",
                            color = Color(0xFFFF3B30),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(remoteTint, RoundedCornerShape(8.dp))
                                .border(1.dp, remoteBorder, RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            Text(
                                text = remoteBody.trimEnd(),
                                color = encoreColors.titleText.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Action row: 60dp hit targets ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onKeepLocal,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                    ) {
                        Text(
                            text = "Keep Local",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Button(
                        onClick = onKeepRemote,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF3B30).copy(alpha = 0.90f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                    ) {
                        Text(
                            text = "Keep Remote",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Cancel ────────────────────────────────────────────────
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Decide Later",
                        color = encoreColors.artistText
                    )
                }
            }
        }
    }
}
