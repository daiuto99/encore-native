package com.encore.tablet.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.encore.core.data.auth.AuthState
import com.encore.core.ui.theme.LocalEncoreColors

/** Orange brand color matching the Encore logo icon. */
private val EncoreOrange = Color(0xFFF07820)

/**
 * Top header bar for the Command Center.
 *
 * Left  — Encore logo wordmark (icon + name + version)
 * Right — Import | SAVE SET | LOAD SET | PERFORM | Dark toggle | Settings | UserAvatar
 *
 * The header sits on [cardBackground] so it reads as a distinct chrome layer
 * above the library content. In dark mode a 1dp bottom border replaces the
 * shadow that would be invisible against a black background.
 *
 * TODO: Replace [Icons.Rounded.MusicNote] with the real Encore icon vector
 *       once an SVG source file is available.
 */
@Composable
fun EncoreHeader(
    authState: AuthState,
    showAccountDropdown: Boolean,
    connectedFolderUri: String?,
    onImportClick: () -> Unit,
    onPerformClick: () -> Unit,
    onSaveSetClick: () -> Unit,
    onLoadSetClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onShowDropdown: () -> Unit,
    onDropdownDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onProfileSheetRequest: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val encoreColors = LocalEncoreColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = encoreColors.cardBackground,
        shadowElevation = if (encoreColors.isDark) 0.dp else 3.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // ── Logo wordmark ────────────────────────────────────────────
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = EncoreOrange,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Encore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = encoreColors.titleText
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "v1.0.2",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Light,
                    color = encoreColors.subtleText
                )

                Spacer(modifier = Modifier.weight(1f))

                // ── Refresh (only when a folder is linked) ───────────────────
                if (connectedFolderUri != null) {
                    IconButton(onClick = onRefreshClick) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh library",
                            tint = encoreColors.iconTint
                        )
                    }
                }

                // ── Import ───────────────────────────────────────────────────
                IconButton(onClick = onImportClick) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Import songs",
                        tint = encoreColors.iconTint
                    )
                }

                // ── SAVE SET ─────────────────────────────────────────────────
                TextButton(onClick = onSaveSetClick) {
                    Text(
                        text = "SAVE SET",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = encoreColors.iconTint
                    )
                }

                // ── LOAD SET ─────────────────────────────────────────────────
                TextButton(onClick = onLoadSetClick) {
                    Text(
                        text = "LOAD SET",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = encoreColors.iconTint
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ── PERFORM ──────────────────────────────────────────────────
                Button(
                    onClick = onPerformClick,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EncoreOrange,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "PERFORM",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ── Dark Mode Toggle ─────────────────────────────────────────
                IconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        imageVector = if (encoreColors.isDark) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay,
                        contentDescription = if (encoreColors.isDark) "Switch to light mode" else "Switch to dark mode",
                        tint = encoreColors.iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // ── Settings ─────────────────────────────────────────────────
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = encoreColors.iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // ── User Avatar + Dropdown ────────────────────────────────────
                Box {
                    IconButton(
                        onClick = {
                            when (authState) {
                                is AuthState.Authenticated -> onShowDropdown()
                                else -> onProfileSheetRequest()
                            }
                        },
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = encoreColors.divider,
                                shape = CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(
                                profilePictureUri = (authState as? AuthState.Authenticated)?.user?.profilePictureUri,
                                isAuthenticated = authState is AuthState.Authenticated,
                                size = 32.dp
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showAccountDropdown,
                        onDismissRequest = onDropdownDismiss
                    ) {
                        val user = (authState as? AuthState.Authenticated)?.user
                        if (user != null) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        val name = user.displayName
                                        if (name != null) {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = user.googleAccountId,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {},
                                enabled = false
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Sign Out") },
                                onClick = onSignOut
                            )
                        }
                    }
                }
            }

            // Bottom border in dark mode (shadow is invisible on dark surfaces)
            if (encoreColors.isDark) {
                HorizontalDivider(
                    color = encoreColors.divider,
                    thickness = 1.dp
                )
            }
        }
    }
}
