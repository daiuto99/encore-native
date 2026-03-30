package com.encore.tablet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.encore.core.data.auth.AuthState
import com.encore.tablet.R

/**
 * Top header bar for the Command Center.
 *
 * Left  — Encore logo + version badge
 * Right — Import | SAVE SET | LOAD SET | PERFORM | Settings | UserAvatar
 *
 * PERFORM / SAVE SET / LOAD SET are no-op placeholders for Phase 4.3.5.
 * Import triggers the SAF file picker via [onImportClick].
 */
@Composable
fun EncoreHeader(
    authState: AuthState,
    showAccountDropdown: Boolean,
    connectedFolderUri: String?,
    onImportClick: () -> Unit,
    onPerformClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onShowDropdown: () -> Unit,
    onDropdownDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onProfileSheetRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Logo ────────────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.encore_logo),
            contentDescription = "Encore",
            modifier = Modifier.height(26.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "v1.0.2",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Refresh (only when a folder is linked) ───────────────────────────
        if (connectedFolderUri != null) {
            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh library",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // ── Import ───────────────────────────────────────────────────────────
        IconButton(onClick = onImportClick) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = "Import songs",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // ── SAVE SET ─────────────────────────────────────────────────────────
        TextButton(onClick = {}) {
            Text(
                text = "SAVE SET",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // ── LOAD SET ─────────────────────────────────────────────────────────
        TextButton(onClick = {}) {
            Text(
                text = "LOAD SET",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // ── PERFORM ──────────────────────────────────────────────────────────
        Button(
            onClick = onPerformClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
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

        Spacer(modifier = Modifier.width(4.dp))

        // ── Settings ─────────────────────────────────────────────────────────
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // ── User Avatar + Dropdown ────────────────────────────────────────────
        Box {
            IconButton(
                onClick = {
                    when (authState) {
                        is AuthState.Authenticated -> onShowDropdown()
                        else -> onProfileSheetRequest()
                    }
                }
            ) {
                Box(
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
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
}
