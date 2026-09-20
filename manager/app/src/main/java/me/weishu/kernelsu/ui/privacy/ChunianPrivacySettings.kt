// SPDX-License-Identifier: GPL-3.0-only
package me.weishu.kernelsu.ui.privacy

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Settings row for both upstream UI modes; component state is the source of truth. */
@Composable
fun ChunianPrivacySettings(miuix: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hidden by remember(context) { mutableStateOf(LauncherPrivacy.isHidden(context)) }
    var showConfirmation by remember { mutableStateOf(false) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hidden = LauncherPrivacy.isHidden(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val applyHidden: (Boolean) -> Unit = { requested ->
        runCatching { LauncherPrivacy.setHidden(context, requested) }
            .onSuccess {
                hidden = LauncherPrivacy.isHidden(context)
                Toast.makeText(
                    context,
                    if (hidden) R.string.chunian_entry_hidden else R.string.chunian_entry_restored,
                    Toast.LENGTH_LONG,
                ).show()
            }
            .onFailure {
                Toast.makeText(context, R.string.chunian_entry_failed, Toast.LENGTH_LONG).show()
            }
    }

    val requestChange: (Boolean) -> Unit = { requested ->
        if (requested) showConfirmation = true else applyHidden(false)
    }

    val title = stringResource(R.string.chunian_privacy_title)
    val summary = stringResource(R.string.chunian_privacy_summary)

    if (miuix) {
        MiuixCard(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
            SwitchPreference(
                title = title,
                summary = summary,
                startAction = {
                    MiuixIcon(
                        Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                checked = hidden,
                onCheckedChange = requestChange,
            )
        }
    } else {
        SegmentedColumn(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            content = listOf {
                SegmentedSwitchItem(
                    icon = Icons.Rounded.VisibilityOff,
                    title = title,
                    summary = summary,
                    checked = hidden,
                    onCheckedChange = requestChange,
                )
            },
        )
    }

    if (showConfirmation) {
        PrivacyConfirmationDialog(
            miuix = miuix,
            onConfirm = {
                showConfirmation = false
                applyHidden(true)
            },
            onDismiss = { showConfirmation = false },
        )
    }
}

@Composable
private fun PrivacyConfirmationDialog(
    miuix: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }
    val title = stringResource(R.string.chunian_privacy_title)
    val summary = stringResource(R.string.chunian_privacy_dialog_summary)
    val dialerNotice = stringResource(R.string.chunian_privacy_confirmation)
    val recoveryNotice = stringResource(R.string.chunian_privacy_recovery)
    val acknowledgement = stringResource(R.string.chunian_privacy_acknowledgement)
    val enableText = stringResource(R.string.chunian_privacy_enable)
    val cancelText = stringResource(android.R.string.cancel)

    if (miuix) {
        SuperDialog(
            show = true,
            title = title,
            summary = summary,
            onDismissRequest = onDismiss,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MiuixText(dialerNotice, fontSize = 14.sp)
                MiuixText(recoveryNotice, fontSize = 14.sp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { acknowledged = !acknowledged }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixCheckbox(
                        state = ToggleableState(acknowledged),
                        onClick = { acknowledged = !acknowledged },
                    )
                    Spacer(Modifier.width(10.dp))
                    MiuixText(
                        text = acknowledgement,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiuixTextButton(
                        text = cancelText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = enableText,
                        onClick = onConfirm,
                        enabled = acknowledged,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    } else {
        MaterialAlertDialog(
            onDismissRequest = onDismiss,
            title = { MaterialText(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MaterialText(summary)
                    MaterialText(dialerNotice)
                    MaterialText(recoveryNotice)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { acknowledged = !acknowledged },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MaterialCheckbox(
                            checked = acknowledged,
                            onCheckedChange = { acknowledged = it },
                        )
                        MaterialText(
                            text = acknowledgement,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            dismissButton = {
                MaterialTextButton(onClick = onDismiss) {
                    MaterialText(cancelText)
                }
            },
            confirmButton = {
                MaterialTextButton(
                    onClick = onConfirm,
                    enabled = acknowledged,
                ) {
                    MaterialText(enableText)
                }
            },
        )
    }
}
