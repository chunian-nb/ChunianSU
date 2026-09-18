// SPDX-License-Identifier: GPL-3.0-only
package me.weishu.kernelsu.ui.privacy

import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
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
                // Also observes recovery with `pm enable`; there is no stale preference to re-hide it.
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
        DisposableEffect(context) {
            val dialog = makeConfirmationDialog(
                context = context,
                onConfirm = { applyHidden(true) },
                onDismiss = { showConfirmation = false },
            )
            onDispose { dialog.dismiss() }
        }
    }
}

/** Acknowledgement is deliberate, not an automatic claim that the dialer was tested. */
private fun makeConfirmationDialog(
    context: Context,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
): AlertDialog {
    val pad = (24 * context.resources.displayMetrics.density).toInt()
    val message = TextView(context).apply {
        setText(R.string.chunian_privacy_confirmation)
    }
    val acknowledgement = CheckBox(context).apply {
        setText(R.string.chunian_privacy_acknowledgement)
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(message)
        addView(acknowledgement)
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.chunian_privacy_title)
        .setView(ScrollView(context).apply { addView(content) })
        .setNegativeButton(android.R.string.cancel) { _, _ -> }
        .setPositiveButton(R.string.chunian_privacy_enable) { _, _ -> onConfirm() }
        .create()
    dialog.setOnDismissListener { onDismiss() }
    dialog.setOnShowListener {
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = false
        acknowledgement.setOnCheckedChangeListener { _, checked -> button.isEnabled = checked }
    }
    dialog.show()
    return dialog
}
